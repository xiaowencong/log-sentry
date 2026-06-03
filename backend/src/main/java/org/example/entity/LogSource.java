package org.example.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 日志源实体 —— 定义要采集的日志文件。
 * <p>
 * 配置文件的路径、格式类型、扫描模式（增量/全量）等。
 * 新建日志源默认使用增量模式，全量扫描需手动触发。
 */
@Data
@Entity
@Table(name = "log_source")
public class LogSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 日志源名称（用户自定义标识）
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 来源类型：FILE（本地文件）
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private SourceType sourceType;

    /**
     * 日志文件完整路径
     */
    @Column(nullable = false, length = 500)
    private String path;

    /**
     * 日志格式类型（如 logback / json / plain / syslog）
     */
    @Column(name = "format_type", nullable = false, length = 30)
    private String formatType;

    /**
     * 扫描模式：INCREMENTAL（增量） / FULL_SCAN（全量）
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "scan_mode", nullable = false, length = 20)
    private ScanMode scanMode;

    /**
     * 批量处理行数（一次读取多少行后提交）
     */
    @Column(name = "batch_size")
    private Integer batchSize;

    /**
     * 批量处理间隔（毫秒）
     */
    @Column(name = "batch_interval_ms")
    private Integer batchIntervalMs;

    /**
     * 解析器配置（JSON 字符串，如自定义日志字段映射）
     */
    @Column(name = "parser_config", columnDefinition = "TEXT")
    private String parserConfig;

    /**
     * 是否启用
     */
    @Column(nullable = false)
    private Boolean enabled = true;

    /**
     * 最近一次采集时间
     */
    @Column(name = "last_collect_time")
    private LocalDateTime lastCollectTime;

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
        if (scanMode == null) {
            scanMode = ScanMode.INCREMENTAL;
        }
        if (sourceType == null) {
            sourceType = SourceType.FILE;
        }
    }
}
