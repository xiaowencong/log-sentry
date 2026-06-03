package org.example.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 问题实体 —— 日志分析引擎识别出的异常/错误事件。
 * <p>
 * 每个 Issue 由指纹（fingerprint）去重，同类日志行汇聚到同一个 Issue。
 * 包含风险评分、AI 分析状态、人工处理状态等完整生命周期信息。
 */
@Data
@Entity
@Table(name = "issue")
public class Issue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 日志指纹，用于去重聚合（全局唯一）
     */
    @Column(nullable = false, length = 64, unique = true)
    private String fingerprint;

    /**
     * 问题一句话摘要（由 LogAggregator 从原始日志生成）
     */
    @Column(length = 500)
    private String summary;

    /**
     * 风险等级：CRITICAL / HIGH / MEDIUM / LOW
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;

    /**
     * 风险评分 0-100，分数越高越危险
     */
    @Column(name = "risk_score")
    private Integer riskScore;

    /**
     * 问题分类（如"数据库连接错误""空指针""超时"等，来自匹配规则）
     */
    @Column(length = 50)
    private String category;

    /**
     * 产生该错误的来源服务名（从日志中解析或由日志源降级填充）
     */
    @Column(name = "service_name", length = 100)
    private String serviceName;

    /**
     * 日志来源路径或配置名称
     */
    @Column(length = 500)
    private String source;

    /**
     * 该问题首次出现的时间
     */
    @Column(name = "first_seen")
    private LocalDateTime firstSeen;

    /**
     * 该问题最近一次出现的时间
     */
    @Column(name = "last_seen")
    private LocalDateTime lastSeen;

    /**
     * 该问题累计出现次数
     */
    @Column(name = "occurrence_count")
    private Integer occurrenceCount = 0;

    /**
     * 人工处理状态：OPEN / ACKNOWLEDGED / RESOLVED / CLOSED
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssueStatus status = IssueStatus.OPEN;

    /**
     * 指派给的处理人
     */
    @Column(name = "assigned_to", length = 50)
    private String assignedTo;

    /**
     * AI 分析状态：PENDING / ANALYZING / COMPLETED / FAILED
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "ai_analysis_status", length = 20)
    private AiAnalysisStatus aiAnalysisStatus = AiAnalysisStatus.PENDING;

    /**
     * 创建时间
     */
    @Column(name = "create_time")
    private LocalDateTime createTime;

    /**
     * 最近更新时间
     */
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
        if (updateTime == null) {
            updateTime = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
