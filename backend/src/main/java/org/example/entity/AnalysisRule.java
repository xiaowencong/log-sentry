package org.example.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 分析规则实体 —— 定义用于匹配日志的规则。
 * <p>
 * 每条规则包含一个正则表达式模式（pattern）和对应的风险等级/评分。
 * 内置规则（isBuiltin=true）由系统预置，不可删除。
 * RuleMatcher 在启动时将所有启用规则编译到内存缓存中。
 */
@Data
@Entity
@Table(name = "analysis_rule")
public class AnalysisRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 规则名称（如"数据库连接错误""空指针异常"）
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 正则表达式匹配模式
     */
    @Column(nullable = false, length = 1000)
    private String pattern;

    /**
     * 匹配该规则时的风险等级
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;

    /**
     * 匹配该规则时的风险评分（0-100）
     */
    @Column(name = "risk_score", nullable = false)
    private Integer riskScore;

    /**
     * 规则分类标签（如"数据库""网络""内存"）
     */
    @Column(length = 50)
    private String category;

    /**
     * 是否启用
     */
    @Column(nullable = false)
    private Boolean enabled = true;

    /**
     * 是否为系统内置规则
     */
    @Column(name = "is_builtin", nullable = false)
    private Boolean isBuiltin = false;

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
