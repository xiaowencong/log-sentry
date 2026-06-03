package org.example.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 单条日志实体 —— 存储被采集的原始 ERROR/WARN 日志行。
 * <p>
 * 每条 LogEntry 关联到一个 Issue，保留完整的原始内容（rawLine）
 * 和解析后的结构化字段（message、level、serviceName 等）。
 */
@Data
@Entity
@Table(name = "log_entry")
public class LogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 日志指纹，与 Issue.fingerprint 一致，用于关联
     */
    @Column(nullable = false, length = 64)
    private String fingerprint;

    /**
     * 关联的问题 ID
     */
    @Column(name = "issue_id")
    private Long issueId;

    /**
     * 日志时间戳
     */
    @Column(nullable = false)
    private LocalDateTime timestamp;

    /**
     * 日志级别（ERROR / WARN / FATAL 等）
     */
    @Column(nullable = false, length = 10)
    private String level;

    /**
     * 来源服务名（解析自日志格式）
     */
    @Column(name = "service_name", length = 100)
    private String serviceName;

    /**
     * 日志来源路径
     */
    @Column(length = 500)
    private String source;

    /**
     * 解析后的核心消息（去除时间戳等前缀）
     */
    @Column(columnDefinition = "TEXT")
    private String message;

    /**
     * 原始日志行（保留完整内容，包含堆栈信息）
     */
    @Column(name = "raw_line", columnDefinition = "TEXT")
    private String rawLine;

    /**
     * 额外元数据（JSON 格式，如线程名、traceId 等）
     */
    @Column(columnDefinition = "TEXT")
    private String metadata;

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
