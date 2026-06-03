package org.example.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * AI 分析结果实体 —— 存储 DeepSeek 对某个 Issue 的分析结论。
 * <p>
 * 包含问题摘要、根因分析、修复建议、风险修正、报错代码位置等信息。
 * 同类问题可复用已有分析结果以节省 API 调用。
 */
@Data
@Entity
@Table(name = "ai_analysis")
public class AiAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的问题 ID（一对一）
     */
    @Column(name = "issue_id", nullable = false)
    private Long issueId;

    /**
     * AI 生成的问题摘要
     */
    @Column(length = 500)
    private String summary;

    /**
     * AI 分析的根因（详细描述）
     */
    @Column(name = "root_cause", columnDefinition = "TEXT")
    private String rootCause;

    /**
     * AI 对风险等级的修正（可覆盖 RiskScorer 的初始判断）
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level_override", length = 20)
    private RiskLevel riskLevelOverride;

    /**
     * 修复建议列表（JSON 字符串数组）
     */
    @Column(name = "suggestions", columnDefinition = "TEXT")
    private String suggestions;

    /**
     * 是否需要立即处理
     */
    @Column(name = "need_immediate_action")
    private Boolean needImmediateAction;

    /**
     * 相关知识点或最佳实践
     */
    @Column(name = "related_knowledge", columnDefinition = "TEXT")
    private String relatedKnowledge;

    /**
     * 使用的 DeepSeek 模型名称
     */
    @Column(name = "deepseek_model_used", length = 50)
    private String deepseekModelUsed;

    /**
     * API 调用耗时（毫秒）
     */
    @Column(name = "api_cost_ms")
    private Long apiCostMs;

    /**
     * 报错代码位置（如 org.example.OrderService.createOrder(OrderService.java:156)）
     */
    @Column(name = "error_location", columnDefinition = "TEXT")
    private String errorLocation;

    /**
     * 用户反馈评分
     */
    @Column(name = "feedback_score")
    private Integer feedbackScore;

    /**
     * 创建时间
     */
    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}
