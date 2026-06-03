package org.example.collector;

public interface LogCollector {

    /**
     * 启动指定日志源的采集
     */
    void start(Long sourceId);

    /**
     * 停止指定日志源的采集
     */
    void stop(Long sourceId);

    /**
     * 触发指定日志源的全量重新扫描
     */
    void triggerFullScan(Long sourceId);

    /**
     * 获取当前扫描进度
     */
    Object getScanProgress(Long sourceId);

    /**
     * 检查日志源是否正在采集
     */
    boolean isActive(Long sourceId);
}
