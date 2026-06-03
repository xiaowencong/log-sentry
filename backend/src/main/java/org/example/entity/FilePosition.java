package org.example.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 文件读取位置实体 —— 记录每个日志源文件的读取偏移量。
 * <p>
 * 用于增量扫描时跟踪上次读到哪一行（lastOffset），
 * 以及通过 lastInode 检测日志文件轮转。
 */
@Data
@Entity
@Table(name = "file_position")
public class FilePosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的日志源 ID
     */
    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    /**
     * 日志文件完整路径
     */
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    /**
     * 上次读取的文件偏移量（字节）
     */
    @Column(name = "last_offset", nullable = false)
    private Long lastOffset = 0L;

    /**
     * 上次读取时的文件 inode（用于检测轮转）
     */
    @Column(name = "last_inode")
    private Long lastInode;

    /**
     * 上次读取时间
     */
    @Column(name = "last_read_time")
    private LocalDateTime lastReadTime;

    /**
     * 创建时间
     */
    @Column(name = "create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
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
