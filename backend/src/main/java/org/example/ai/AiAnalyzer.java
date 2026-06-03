package org.example.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.*;
import org.example.repository.AiAnalysisRepository;
import org.example.repository.IssueRepository;
import org.example.repository.LogEntryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI 分析编排器 —— 管理完整的 AI 分析流水线。
 * <p>
 * 流水线：缓存检查 → 同类复用 → 频率限制 → 构建提示词 → 调用 API → 解析 → 保存结果 → 更新状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiAnalyzer {

    private final DeepSeekClient deepSeekClient;
    private final PromptBuilder promptBuilder;
    private final RateLimiter rateLimiter;
    private final AiResultCache resultCache;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final IssueRepository issueRepository;
    private final LogEntryRepository logEntryRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 使用 DeepSeek AI 分析单个问题。
     * <p>
     * 事务保护：防止并发场景下同一 Issue 被重复分析、重复插入 AiAnalysis 记录。
     *
     * @param issue 待分析的问题
     * @return true 表示分析成功完成，false 表示失败或延期
     */
    @Transactional
    public boolean analyze(Issue issue) {
        Long issueId = issue.getId();

        // 步骤 1：检查是否已有分析结果
        AiAnalysis existing = aiAnalysisRepository.findByIssueId(issueId).orElse(null);
        if (existing != null && existing.getSummary() != null && !existing.getSummary().isEmpty()) {
            // 同步 Issue 状态（防止此前更新丢失）
            if (issue.getAiAnalysisStatus() != AiAnalysisStatus.COMPLETED) {
                issue.setAiAnalysisStatus(AiAnalysisStatus.COMPLETED);
                issueRepository.save(issue);
            }
            log.debug("问题 #{} 已有 AI 分析结果，跳过", issueId);
            return true;
        }

        // 步骤 1.5：同类别复用，避免重复调用 API
        String category = issue.getCategory();
        if (category != null && !category.isEmpty()) {
            List<Issue> sameCategory = issueRepository.findCompletedByCategory(
                    category, AiAnalysisStatus.COMPLETED, issueId, PageRequest.of(0, 1));
            for (Issue similar : sameCategory) {
                AiAnalysis similarAnalysis = aiAnalysisRepository.findByIssueId(similar.getId()).orElse(null);
                if (similarAnalysis != null && similarAnalysis.getSummary() != null
                        && !similarAnalysis.getSummary().isEmpty()) {
                    // 复制分析结果到当前问题
                    AiAnalysis copy = new AiAnalysis();
                    copy.setIssueId(issueId);
                    copy.setSummary(similarAnalysis.getSummary());
                    copy.setRootCause(similarAnalysis.getRootCause());
                    copy.setRiskLevelOverride(similarAnalysis.getRiskLevelOverride());
                    copy.setNeedImmediateAction(similarAnalysis.getNeedImmediateAction());
                    copy.setRelatedKnowledge(similarAnalysis.getRelatedKnowledge());
                    copy.setSuggestions(similarAnalysis.getSuggestions());
                    copy.setDeepseekModelUsed(similarAnalysis.getDeepseekModelUsed() + " (复用)");
                    copy.setApiCostMs(0L);
                    copy.setErrorLocation(similarAnalysis.getErrorLocation());
                    aiAnalysisRepository.save(copy);

                    issue.setAiAnalysisStatus(AiAnalysisStatus.COMPLETED);
                    if (similarAnalysis.getRiskLevelOverride() != null) {
                        issue.setRiskLevel(similarAnalysis.getRiskLevelOverride());
                    }
                    issueRepository.save(issue);

                    resultCache.put(issue.getFingerprint(), copy);
                    log.info("复用问题 #{} 的 AI 分析结果（同类 {}） → 问题 #{}",
                            similar.getId(), category, issueId);
                    return true;
                }
            }
        }

        // 步骤 2：检查频率限制
        if (!rateLimiter.tryAcquire()) {
            log.debug("触发频率限制，问题 #{} 延期处理", issueId);
            return false;
        }

        // 步骤 3：标记为"分析中"
        issue.setAiAnalysisStatus(AiAnalysisStatus.ANALYZING);
        issueRepository.save(issue);

        // 步骤 4：获取关联的日志条目（供 AI 分析上下文）
        List<LogEntry> logEntries = logEntryRepository.findByIssueIdOrderByTimestampAsc(issueId);

        // 步骤 5：构建提示词
        String systemPrompt = promptBuilder.getSystemPrompt();
        String userMessage = promptBuilder.buildUserMessage(issue, logEntries);

        // 步骤 6：调用 DeepSeek API
        long startMs = System.currentTimeMillis();
        DeepSeekResponse response = deepSeekClient.chat(systemPrompt, userMessage);
        long costMs = System.currentTimeMillis() - startMs;

        // 步骤 7：处理 API 调用失败
        if (response == null || response.getContent() == null) {
            issue.setAiAnalysisStatus(AiAnalysisStatus.FAILED);
            issueRepository.save(issue);
            log.error("问题 #{} AI 分析 API 调用失败", issueId);
            return false;
        }

        // 步骤 8：解析 API 响应
        AiAnalysisResult result = promptBuilder.parseResponse(response.getContent());
        if (result == null) {
            issue.setAiAnalysisStatus(AiAnalysisStatus.FAILED);
            issueRepository.save(issue);
            log.error("问题 #{} AI 响应解析失败", issueId);
            return false;
        }

        // 步骤 9：保存分析结果（捕获并发插入导致的唯一约束冲突）
        AiAnalysis analysis = new AiAnalysis();
        analysis.setIssueId(issueId);
        analysis.setSummary(result.getSummary());
        analysis.setRootCause(result.getRootCause());
        analysis.setRiskLevelOverride(parseRiskLevel(result.getRiskLevel()));
        analysis.setNeedImmediateAction(result.getNeedImmediateAction());
        analysis.setRelatedKnowledge(result.getRelatedKnowledge());
        analysis.setDeepseekModelUsed(response.getModel());
        analysis.setApiCostMs(costMs);
        analysis.setErrorLocation(result.getErrorLocation());

        // 序列化处理建议列表为 JSON 字符串
        if (result.getSuggestions() != null) {
            try {
                analysis.setSuggestions(objectMapper.writeValueAsString(result.getSuggestions()));
            } catch (Exception e) {
                analysis.setSuggestions("[]");
            }
        }

        try {
            aiAnalysisRepository.save(analysis);
        } catch (DataIntegrityViolationException e) {
            // 并发场景下另一个线程已插入同 issueId 的分析结果，复用已有记录
            log.info("问题 #{} AI 分析结果已被其他线程插入，使用已有记录", issueId);
            AiAnalysis concurrent = aiAnalysisRepository.findByIssueId(issueId).orElse(null);
            if (concurrent != null) {
                analysis = concurrent;
            } else {
                throw e;
            }
        }

        // 步骤 10：更新问题状态（先 re-load 确保拿到托管实体）
        Issue managedIssue = issueRepository.findById(issueId).orElse(null);
        if (managedIssue != null) {
            managedIssue.setAiAnalysisStatus(AiAnalysisStatus.COMPLETED);
            if (analysis.getRiskLevelOverride() != null) {
                managedIssue.setRiskLevel(analysis.getRiskLevelOverride());
            }
            issueRepository.save(managedIssue);
        } else {
            log.error("问题 #{} 更新 AI 状态时未找到该问题", issueId);
        }

        // 步骤 11：缓存结果供后续复用
        resultCache.put(issue.getFingerprint(), analysis);

        log.info("AI 分析完成 #{}: 摘要={}, 风险={}, 耗时={}ms",
                issueId,
                result.getSummary() != null ? result.getSummary().substring(0, Math.min(50, result.getSummary().length())) : "",
                result.getRiskLevel(), costMs);

        return true;
    }

    private RiskLevel parseRiskLevel(String level) {
        if (level == null) return null;
        try {
            return RiskLevel.valueOf(level.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
