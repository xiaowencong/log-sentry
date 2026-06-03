package org.example.analyzer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.collector.parser.ParsedLogEntry;
import org.example.entity.AiAnalysisStatus;
import org.example.entity.AnalysisRule;
import org.example.entity.Issue;
import org.example.entity.RiskLevel;
import org.example.repository.IssueRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 核心分析引擎 —— 编排规则匹配、风险评分和日志聚合为 Issue 的完整流水线。
 * <p>
 * 流水线：解析 → 匹配规则 → 风险评分 → 聚合为 Issue → 保存日志条目。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisEngine {

    private final RuleMatcher ruleMatcher;
    private final RiskScorer riskScorer;
    private final LogAggregator logAggregator;
    private final IssueRepository issueRepository;

    /**
     * 分析一条已解析的日志并生成/更新 Issue。
     *
     * @param parsed 已解析的日志条目
     * @param source 日志来源名称（文件路径或配置名）
     * @return 包含 Issue 和是否需要 AI 分析的结果
     */
    public AnalysisResult analyze(ParsedLogEntry parsed, String source) {
        // 步骤 1：规则匹配
        List<AnalysisRule> matchedRules = ruleMatcher.match(parsed.getMessage(), parsed.getRawLine());

        // 步骤 2：从最优先匹配的规则中获取分类
        String category = "其他";
        if (!matchedRules.isEmpty()) {
            String ruleCategory = matchedRules.get(0).getCategory();
            if (ruleCategory != null && !ruleCategory.isEmpty()) {
                category = ruleCategory;
            }
        }

        // 步骤 3：风险评分（初始出现次数为 1，后续由 LogAggregator 调整）
        RiskScorer.ScoredResult scored = riskScorer.compute(matchedRules, parsed.getLevel(), 1);

        // 步骤 4：查找已有 Issue 或创建新 Issue（通过指纹去重）
        Issue issue = logAggregator.findOrCreateIssue(parsed, source,
                scored.getRiskLevel(), scored.getRiskScore(), category);

        // 步骤 5：如果 Issue 已存在，用实际出现次数重新计算风险
        if (issue.getOccurrenceCount() > 1) {
            RiskScorer.ScoredResult rescored = riskScorer.compute(
                    matchedRules, parsed.getLevel(), issue.getOccurrenceCount());
            issue.setRiskLevel(rescored.getRiskLevel());
            issue.setRiskScore(rescored.getRiskScore());
        }

        // 步骤 6：保存原始日志条目
        logAggregator.saveLogEntry(parsed, source, issue);

        // 步骤 7：判断是否需要 AI 分析
        // 仅当尚未分析且未在分析中时才设为 PENDING
        boolean needAi = shouldTriggerAi(issue, matchedRules);
        if (needAi
                && issue.getAiAnalysisStatus() != AiAnalysisStatus.COMPLETED
                && issue.getAiAnalysisStatus() != AiAnalysisStatus.ANALYZING) {
            issue.setAiAnalysisStatus(AiAnalysisStatus.PENDING);
            issueRepository.save(issue);
        }

        log.debug("分析完成: 问题#{} 级别:{}, 评分:{}, 次数:{}, 需AI:{}",
                issue.getId(), issue.getRiskLevel(), issue.getRiskScore(),
                issue.getOccurrenceCount(), needAi);

        return new AnalysisResult(issue, matchedRules, needAi);
    }

    /**
     * 判断是否需要发送给 DeepSeek AI 进行分析。
     * 触发条件：CRITICAL/HIGH 级别，或未知规则匹配的 MEDIUM 级别。
     */
    private boolean shouldTriggerAi(Issue issue, List<AnalysisRule> matchedRules) {
        // CRITICAL 和 HIGH 级别始终触发
        if (issue.getRiskLevel() == RiskLevel.CRITICAL || issue.getRiskLevel() == RiskLevel.HIGH) {
            return true;
        }
        // MEDIUM 且未匹配到任何规则时触发（未知异常值得关注）
        if (issue.getRiskLevel() == RiskLevel.MEDIUM && matchedRules.isEmpty()) {
            return true;
        }
        return false;
    }

    /**
     * 分析结果：包含 Issue、匹配的规则列表和是否需要 AI 分析。
     */
    public static class AnalysisResult {
        private final Issue issue;
        private final List<AnalysisRule> matchedRules;
        private final boolean needAiAnalysis;

        public AnalysisResult(Issue issue, List<AnalysisRule> matchedRules, boolean needAiAnalysis) {
            this.issue = issue;
            this.matchedRules = matchedRules;
            this.needAiAnalysis = needAiAnalysis;
        }

        public Issue getIssue() {
            return issue;
        }

        public List<AnalysisRule> getMatchedRules() {
            return matchedRules;
        }

        public boolean isNeedAiAnalysis() {
            return needAiAnalysis;
        }
    }
}
