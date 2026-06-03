package org.example.dto;

/**
 * 扫描进度 DTO —— 返回给前端展示日志源的扫描进度状态。
 * <p>
 * 注意：可修改字段使用 volatile 保证跨线程内存可见性。
 * 此对象被线程池线程原地修改，同时被 HTTP 轮询线程读取，
 * 若无 volatile，轮询线程可能永远看不到最新值。
 */
public class ScanProgressDTO {
    /**
     * 日志源 ID
     */
    private volatile Long sourceId;
    /**
     * 状态：IDLE（空闲）/ SCANNING（扫描中）/ TAILING（监听中）/ COMPLETED（完成）
     */
    private volatile String status;
    /**
     * 文件预估总行数
     */
    private volatile Long totalLines;
    /**
     * 已读取行数
     */
    private volatile Long readLines;
    /**
     * 进度百分比 0-100
     */
    private volatile Integer progressPercent;
    /**
     * 已发现的问题数
     */
    private volatile Integer issuesFound;
    /**
     * 预估剩余秒数
     */
    private volatile Integer estimatedRemainingSeconds;

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getTotalLines() {
        return totalLines;
    }

    public void setTotalLines(Long totalLines) {
        this.totalLines = totalLines;
    }

    public Long getReadLines() {
        return readLines;
    }

    public void setReadLines(Long readLines) {
        this.readLines = readLines;
    }

    public Integer getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(Integer progressPercent) {
        this.progressPercent = progressPercent;
    }

    public Integer getIssuesFound() {
        return issuesFound;
    }

    public void setIssuesFound(Integer issuesFound) {
        this.issuesFound = issuesFound;
    }

    public Integer getEstimatedRemainingSeconds() {
        return estimatedRemainingSeconds;
    }

    public void setEstimatedRemainingSeconds(Integer estimatedRemainingSeconds) {
        this.estimatedRemainingSeconds = estimatedRemainingSeconds;
    }
}
