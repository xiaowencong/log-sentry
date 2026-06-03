package org.example.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 报表实体 —— 存储生成的报告内容。
 * <p>
 * 支持日报/周报/月报/自定义时间段报告，内容为 Markdown 格式，
 * 可通过接口下载为 .md 文件。
 */
@Data
@Entity
@Table(name = "report")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 报表类型：DAILY / WEEKLY / MONTHLY / CUSTOM
     */
    @Column(nullable = false, length = 20)
    private String type;

    /**
     * 报表标题
     */
    @Column(length = 200)
    private String title;

    /**
     * 统计开始时间
     */
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    /**
     * 统计结束时间
     */
    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    /**
     * 问题总数
     */
    @Column(name = "total_issues")
    private Integer totalIssues;

    /**
     * 严重（CRITICAL）问题数
     */
    @Column(name = "critical_count")
    private Integer criticalCount;

    /**
     * 高危（HIGH）问题数
     */
    @Column(name = "high_count")
    private Integer highCount;

    /**
     * 中等（MEDIUM）问题数
     */
    @Column(name = "medium_count")
    private Integer mediumCount;

    /**
     * 低风险（LOW）问题数
     */
    @Column(name = "low_count")
    private Integer lowCount;

    /**
     * 报表正文内容（Markdown 格式）
     */
    @Column(name = "content", columnDefinition = "MEDIUMTEXT")
    private String content;

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
