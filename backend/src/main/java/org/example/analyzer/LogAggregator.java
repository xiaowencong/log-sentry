package org.example.analyzer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.collector.parser.ParsedLogEntry;
import org.example.common.LogFingerprint;
import org.example.entity.*;
import org.example.repository.IssueRepository;
import org.example.repository.LogEntryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 日志聚合器 —— 将同指纹日志行聚合到同一个 Issue，避免告警风暴。
 * <p>
 * 按指纹查找已有 Issue，未找到则新建；支持时间窗口内聚合。
 * 处理并发场景下的指纹冲突（DataIntegrityViolationException 兜底重试）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogAggregator {

    private final IssueRepository issueRepository;
    private final LogEntryRepository logEntryRepository;
    private final EntityManager entityManager;

    @Value("${log-sentry.aggregator.window-minutes:5}")
    private int aggregationWindowMinutes;

    /**
     * 查找已有 Issue 或创建新 Issue。
     * <p>
     * 同名指纹 Issue 仅在时间窗口内聚合；超出窗口则创建新 Issue，避免跨月错误被错误聚合。
     * <p>
     * 注意：不在方法级使用 @Transactional，因为并发指纹冲突时 save 抛异常
     * 会将外层事务标记为 rollback-only，导致后续重试查询也失败。
     * 各 save 操作使用 Spring Data JPA 默认的隐式事务即可。
     *
     * @param parsed    已解析的日志条目
     * @param source    日志来源名称
     * @param riskLevel 风险等级
     * @param riskScore 风险评分
     * @param category  匹配到的规则分类（可为 null）
     * @return 已有或新建的 Issue
     */
    public Issue findOrCreateIssue(ParsedLogEntry parsed, String source,
                                   RiskLevel riskLevel, int riskScore, String category) {
        String fingerprint = LogFingerprint.compute(parsed.getRawLine());

        // 按指纹查找已有 Issue
        Optional<Issue> existingOpt = issueRepository.findByFingerprint(fingerprint);
        if (existingOpt.isPresent()) {
            Issue existing = existingOpt.get();
            // 检查时间窗口：超过窗口则视为新问题，不聚合
            if (existing.getLastSeen() != null) {
                LocalDateTime windowStart = LocalDateTime.now().minusMinutes(aggregationWindowMinutes);
                if (existing.getLastSeen().isBefore(windowStart)) {
                    log.debug("指纹 {} 的已有问题最后出现时间 {} 超出聚合窗口 {} 分钟，创建新问题",
                            fingerprint, existing.getLastSeen(), aggregationWindowMinutes);
                    // 不 return，继续往下走创建新 Issue
                } else {
                    return updateExistingIssue(existing, source);
                }
            } else {
                return updateExistingIssue(existing, source);
            }
        }

        // 新建 Issue —— 捕获唯一索引冲突后重新查询（处理并发竞争）
        try {
            return createNewIssue(fingerprint, parsed, source, riskLevel, riskScore, category);
        } catch (DataIntegrityViolationException e) {
            // 并发线程在查询和插入之间插入了相同指纹
            // 必须先清空 EntityManager，否则 save 失败后 Session 中有脏实体（id=null），
            // 后续查询触发 auto-flush 会导致 Hibernate AssertionFailure
            entityManager.clear();
            log.debug("指纹冲突 {}, 重新查询已有问题", fingerprint);
            existingOpt = issueRepository.findByFingerprint(fingerprint);
            if (existingOpt.isPresent()) {
                return updateExistingIssue(existingOpt.get(), source);
            }
            // 理论上不会走到这里，兜底抛出原始异常
            throw e;
        }
    }

    /**
     * 更新已有 Issue：增加出现次数、更新最后出现时间、高频时提升风险等级。
     */
    private Issue updateExistingIssue(Issue existing, String source) {
        existing.setOccurrenceCount(existing.getOccurrenceCount() + 1);
        existing.setLastSeen(LocalDateTime.now());
        // 回填 serviceName（如果之前未设置）
        if (existing.getServiceName() == null || existing.getServiceName().isEmpty()) {
            existing.setServiceName(source);
        }
        // 高频出现时自动提升风险等级
        if (existing.getOccurrenceCount() > 20 && existing.getRiskScore() < 70) {
            existing.setRiskScore(Math.min(existing.getRiskScore() + 10, 100));
            existing.setRiskLevel(mapScoreToLevel(existing.getRiskScore()));
        }
        return issueRepository.save(existing);
    }

    /**
     * 创建新 Issue，设置初始摘要、风险评分、分类等信息。
     */
    private Issue createNewIssue(String fingerprint, ParsedLogEntry parsed, String source,
                                 RiskLevel riskLevel, int riskScore, String category) {
        Issue issue = new Issue();
        issue.setFingerprint(fingerprint);
        issue.setSummary(buildSummary(parsed, source));
        issue.setRiskLevel(riskLevel);
        issue.setRiskScore(riskScore);
        issue.setCategory(category);
        issue.setServiceName(parsed.getServiceName() != null && !parsed.getServiceName().isEmpty()
                ? parsed.getServiceName() : source);
        issue.setSource(source);
        issue.setFirstSeen(parsed.getTimestamp() != null ? parsed.getTimestamp() : LocalDateTime.now());
        issue.setLastSeen(parsed.getTimestamp() != null ? parsed.getTimestamp() : LocalDateTime.now());
        issue.setOccurrenceCount(1);
        issue.setStatus(IssueStatus.OPEN);
        issue.setAiAnalysisStatus(AiAnalysisStatus.PENDING);

        return issueRepository.save(issue);
    }

    /**
     * 保存原始日志条目并关联到指定 Issue。
     */
    public LogEntry saveLogEntry(ParsedLogEntry parsed, String source, Issue issue) {
        String fingerprint = LogFingerprint.compute(parsed.getRawLine());

        LogEntry entry = new LogEntry();
        entry.setFingerprint(fingerprint);
        entry.setIssueId(issue.getId());
        entry.setTimestamp(parsed.getTimestamp() != null ? parsed.getTimestamp() : LocalDateTime.now());
        entry.setLevel(parsed.getLevel());
        entry.setServiceName(parsed.getServiceName());
        entry.setSource(source);
        entry.setMessage(parsed.getMessage());
        entry.setRawLine(parsed.getRawLine());
        // 序列化元数据为 JSON 字符串
        if (parsed.getMetadata() != null && !parsed.getMetadata().isEmpty()) {
            try {
                entry.setMetadata(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(parsed.getMetadata()));
            } catch (Exception ignored) {
            }
        }

        return logEntryRepository.save(entry);
    }

    /**
     * 构建 Issue 摘要，格式：[服务名] 消息内容（截断至 120 字符）。
     * 服务名优先取日志解析结果，回退到日志源名称。
     */
    private String buildSummary(ParsedLogEntry parsed, String source) {
        String service = parsed.getServiceName() != null && !parsed.getServiceName().isEmpty()
                ? parsed.getServiceName() : source;
        if (service == null || service.isEmpty()) {
            service = "unknown";
        }
        String msg = parsed.getMessage();
        if (msg == null || msg.trim().isEmpty()) {
            msg = parsed.getLevel() + " log entry";
        } else if (msg.length() > 120) {
            msg = msg.substring(0, 120) + "...";
        }
        return "[" + service + "] " + msg;
    }

    /**
     * 将评分映射为风险等级：≥90=CRITICAL, ≥70=HIGH, ≥40=MEDIUM, <40=LOW。
     */
    private RiskLevel mapScoreToLevel(int score) {
        if (score >= 90) return RiskLevel.CRITICAL;
        if (score >= 70) return RiskLevel.HIGH;
        if (score >= 40) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }
}
