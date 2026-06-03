package org.example.collector;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.analyzer.AnalysisEngine;
import org.example.analyzer.LogAggregator;
import org.example.collector.parser.LogParser;
import org.example.collector.parser.ParsedLogEntry;
import org.example.dto.ScanProgressDTO;
import org.example.entity.Issue;
import org.example.entity.LogSource;
import org.example.entity.ScanMode;
import org.example.repository.IssueRepository;
import org.example.repository.LogSourceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileTailerService implements LogCollector {

    /**
     * 堆栈追踪续行模式：以空白开头后跟 "at "、"Caused by:" 等的行
     */
    private static final Pattern STACK_TRACE = Pattern.compile(
            "^\\s+(?:at\\s+|Caused\\s+by:|Suppressed:|...\\s+\\d+\\s+more)");
    private final LogSourceRepository logSourceRepository;
    private final FilePositionTracker positionTracker;
    private final List<LogParser> parsers;
    private final AnalysisEngine analysisEngine;
    private final LogAggregator logAggregator;
    private final IssueRepository issueRepository;
    /**
     * 每个日志源最近一次产生的问题，用于堆栈行聚合
     */
    private final Map<Long, Issue> lastIssues = new ConcurrentHashMap<>();

    /**
     * 每个日志源当前会话中发现的唯一问题 ID 集合
     */
    private final Map<Long, Set<Long>> sessionIssueIds = new ConcurrentHashMap<>();

    /**
     * 本次扫描中已通过 INFO 日志输出的新问题计数（限制前 5 条）
     */
    private final AtomicInteger newIssueLogCount = new AtomicInteger(0);
    private final Map<Long, TailTask> activeTasks = new ConcurrentHashMap<>();
    private final Map<Long, ScanProgressDTO> scanProgressMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
            Runtime.getRuntime().availableProcessors() * 2
    );
    @Value("${log-sentry.collector.poll-interval:500}")
    private int pollInterval;
    @Value("${log-sentry.collector.max-batch-lines:5000}")
    private int maxBatchLines;
    @Value("${log-sentry.collector.full-scan-batch-lines:10000}")
    private int fullScanBatchLines;
    @Value("${log-sentry.collector.full-scan-batch-interval:100}")
    private int fullScanBatchInterval;

    @Override
    public void start(Long sourceId) {
        if (activeTasks.containsKey(sourceId)) {
            log.warn("日志源 {} 已在监听中", sourceId);
            return;
        }

        LogSource source = logSourceRepository.findById(sourceId).orElse(null);
        if (source == null || !source.getEnabled()) {
            log.warn("日志源 {} 不存在或已禁用", sourceId);
            return;
        }

        TailTask task = new TailTask(sourceId);
        activeTasks.put(sourceId, task);

        // 新会话，清空旧的问题 ID 集合
        sessionIssueIds.put(sourceId, new HashSet<>());

        if (source.getScanMode() == ScanMode.FULL_SCAN) {
            task.fullScanMode = true;
            scheduler.execute(() -> fullScan(source, task));
        } else {
            scheduler.execute(() -> tail(source, task));
        }

        log.info("开始监听日志源: {} ({})", source.getName(), source.getPath());
    }

    @Override
    public void stop(Long sourceId) {
        TailTask task = activeTasks.remove(sourceId);
        if (task != null) {
            task.stop();
            scanProgressMap.remove(sourceId);
            sessionIssueIds.remove(sourceId);
            log.info("停止监听日志源: {}", sourceId);
        }
    }

    @Override
    public void triggerFullScan(Long sourceId) {
        // 防止重复触发全量扫描——先检查当前进度
        ScanProgressDTO existingProgress = scanProgressMap.get(sourceId);
        if (existingProgress != null && "SCANNING".equals(existingProgress.getStatus())) {
            throw new IllegalStateException("日志源正在扫描中，请等待完成后再试");
        }

        TailTask existingTask = activeTasks.get(sourceId);
        if (existingTask != null) {
            existingTask.stop();
            activeTasks.remove(sourceId);
        }

        LogSource source = logSourceRepository.findById(sourceId).orElse(null);
        if (source == null) return;

        // 重置偏移量，从头开始
        positionTracker.resetPosition(sourceId, source.getPath());

        // 重置进度（所有字段初始化为 0，避免前端因 null 显示为 0）
        sessionIssueIds.put(sourceId, new HashSet<>());
        ScanProgressDTO progress = new ScanProgressDTO();
        progress.setSourceId(sourceId);
        progress.setStatus("SCANNING");
        progress.setReadLines(0L);
        progress.setProgressPercent(0);
        progress.setIssuesFound(0);
        scanProgressMap.put(sourceId, progress);

        TailTask task = new TailTask(sourceId);
        task.fullScanMode = true;
        activeTasks.put(sourceId, task);
        scheduler.execute(() -> fullScan(source, task));
    }

    @Override
    public ScanProgressDTO getScanProgress(Long sourceId) {
        return scanProgressMap.get(sourceId);
    }

    @Override
    public boolean isActive(Long sourceId) {
        return activeTasks.containsKey(sourceId);
    }

    private void tail(LogSource source, TailTask task) {
        String filePath = source.getPath();
        long offset = positionTracker.getLastOffset(source.getId(), filePath);
        long lastInode = positionTracker.getLastInode(source.getId(), filePath);

        log.info("增量监听 {} 从偏移量 {} 开始", filePath, offset);

        // 重置新问题日志计数器
        newIssueLogCount.set(0);

        // 初始化增量扫描进度（保留上次全量扫描的 readLines 和 totalLines）
        ScanProgressDTO existingProgress = scanProgressMap.get(source.getId());
        ScanProgressDTO progress = new ScanProgressDTO();
        progress.setSourceId(source.getId());
        progress.setStatus("TAILING");
        progress.setReadLines(0L);
        progress.setProgressPercent(100);
        progress.setIssuesFound(0);
        // 从上次扫描结果中继承已读取行数和总行数
        if (existingProgress != null) {
            if (existingProgress.getReadLines() != null) progress.setReadLines(existingProgress.getReadLines());
            if (existingProgress.getTotalLines() != null) progress.setTotalLines(existingProgress.getTotalLines());
        }
        scanProgressMap.put(source.getId(), progress);

        // 复用当前会话的问题 ID 集合（从全量扫描继承，精确统计唯一问题数）
        Set<Long> foundIssueIds = sessionIssueIds.computeIfAbsent(source.getId(), k -> new HashSet<>());
        progress.setIssuesFound(foundIssueIds.size());

        while (task.running) {
            try {
                File file = new File(filePath);
                if (!file.exists()) {
                    TimeUnit.MILLISECONDS.sleep(pollInterval);
                    continue;
                }

                long currentInode = getFileInode(file);
                long fileSize = file.length();

                // 检测日志轮转：文件变小了（被截断或替换）
                // Windows 上 inode 检测不可靠，只使用 fileSize < offset 作为轮转信号
                if (fileSize < offset) {
                    log.info("检测到日志轮转 {} (文件大小 {} < 偏移量 {}, inode {} -> {})",
                            filePath, fileSize, offset, lastInode, currentInode);
                    offset = 0;
                }

                if (fileSize > offset) {
                    int linesRead = 0;
                    try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                        raf.seek(offset);
                        String line;
                        while ((line = raf.readLine()) != null && linesRead < maxBatchLines) {
                            linesRead++;
                            Issue issue = processLine(line, source);
                            if (issue != null) {
                                foundIssueIds.add(issue.getId());
                            }
                        }
                        offset = raf.getFilePointer();
                    }

                    positionTracker.savePosition(source.getId(), filePath, offset, currentInode);
                    lastInode = currentInode;

                    // 更新增量扫描进度（使用唯一问题 ID 集合精确统计）
                    if (linesRead > 0) {
                        long totalRead = progress.getReadLines() != null ? progress.getReadLines() : 0L;
                        progress.setReadLines(totalRead + linesRead);
                        progress.setIssuesFound(foundIssueIds.size());
                        // 更新日志源的最后采集时间
                        source.setLastCollectTime(LocalDateTime.now());
                        logSourceRepository.save(source);
                        log.debug("增量读取 {} 行 | 文件:{} | 累计读取:{}, 累计问题:{}",
                                linesRead, filePath, progress.getReadLines(), foundIssueIds.size());
                    }
                }

                TimeUnit.MILLISECONDS.sleep(pollInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("监听 {} 出错: {}", filePath, e.getMessage());
                try {
                    TimeUnit.MILLISECONDS.sleep(pollInterval);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("日志源 {} 监听已停止", source.getId());
    }

    // ========== 增量监听 ==========

    private void fullScan(LogSource source, TailTask task) {
        String filePath = source.getPath();
        long offset = positionTracker.getLastOffset(source.getId(), filePath);

        File file = new File(filePath);
        if (!file.exists()) {
            log.warn("全量扫描文件不存在: {}", filePath);
            scanProgressMap.remove(source.getId());
            return;
        }

        long fileSize = file.length();
        log.info("全量扫描开始 [{}] | 文件:{} | 偏移:{} | 文件大小:{} | 任务进行中:{}",
                source.getName(), filePath, offset, fileSize, task.running);

        // 确保全量扫描从文件头部开始
        if (offset >= fileSize) {
            log.warn("全量扫描偏移量 ({}) >= 文件大小 ({}), 强制从头部开始", offset, fileSize);
            offset = 0;
        }

        long totalLines;
        try {
            totalLines = countLines(file);
        } catch (Exception e) {
            log.error("全量扫描统计行数错误: {}", e.getMessage());
            totalLines = -1;
        }

        ScanProgressDTO progress = scanProgressMap.computeIfAbsent(source.getId(), id -> {
            ScanProgressDTO p = new ScanProgressDTO();
            p.setSourceId(source.getId());
            p.setStatus("SCANNING");
            p.setReadLines(0L);
            p.setProgressPercent(0);
            p.setIssuesFound(0);
            return p;
        });
        progress.setTotalLines(totalLines);

        log.info("全量扫描启动 {} (总行数估计: ~{})", filePath, totalLines);

        // 重置新问题日志计数器
        newIssueLogCount.set(0);

        // 本次扫描的唯一问题 ID 集合（精确统计，避免堆栈续行重复计数）
        Set<Long> foundIssueIds = sessionIssueIds.computeIfAbsent(source.getId(), k -> new HashSet<>());

        // 动态计算进度刷新间隔：目标约 50 次更新，最少 10 行一次
        long progressInterval = totalLines > 0 ? Math.max(10, totalLines / 50) : 100;

        long batchStartOffset = offset;
        long totalRead = 0;
        long lastLogLine = 0;           // 上次打印进度日志时的行号
        int totalSkipped = 0;            // 跳过的行数（非 ERROR/WARN）
        int totalParsed = 0;            // 成功解析并保留的行数

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(offset);

            String line;
            int batchCount = 0;

            while ((line = raf.readLine()) != null && task.running) {
                batchCount++;
                totalRead++;

                Issue issue = processLine(line, source);
                if (issue != null) {
                    foundIssueIds.add(issue.getId());
                    totalParsed++;
                } else {
                    totalSkipped++;
                }

                // 动态间隔刷新前端进度条（根据文件行数自适应，目标 ~50 次更新）
                if (totalRead % progressInterval == 0) {
                    progress.setReadLines(totalRead);
                    if (totalLines > 0) {
                        progress.setProgressPercent((int) (totalRead * 100 / totalLines));
                    }
                    progress.setIssuesFound(foundIssueIds.size());
                }

                // 每 500 行打印一次进度日志
                if (totalRead - lastLogLine >= 500) {
                    log.info("全量扫描进度 [{}]: {} / {} 行 (~{}%), 匹配 {} | 跳过 {}",
                            source.getName(), totalRead, totalLines,
                            totalLines > 0 ? (totalRead * 100 / totalLines) : -1,
                            totalParsed, totalSkipped);
                    lastLogLine = totalRead;
                }

                // 批量提交：每全量扫描批次后保存偏移量和进度
                if (batchCount >= fullScanBatchLines) {
                    long currentOffset = raf.getFilePointer();
                    positionTracker.savePosition(source.getId(), filePath, currentOffset, getFileInode(file));
                    batchStartOffset = currentOffset;
                    batchCount = 0;

                    // 更新扫描进度
                    progress.setReadLines(totalRead);
                    progress.setIssuesFound(foundIssueIds.size());
                    if (totalLines > 0) {
                        progress.setProgressPercent((int) (totalRead * 100 / totalLines));
                    }

                    // 批次间稍作停顿，避免 CPU 满载
                    TimeUnit.MILLISECONDS.sleep(fullScanBatchInterval);
                }
            }

            // 最后一轮提交
            if (batchCount > 0) {
                long finalOffset = raf.getFilePointer();
                positionTracker.savePosition(source.getId(), filePath, finalOffset, getFileInode(file));
            }

            progress.setReadLines(totalRead);
            progress.setStatus("COMPLETED");
            progress.setProgressPercent(100);
            progress.setIssuesFound(foundIssueIds.size());
            log.info("全量扫描完成 [{}]: 读取 {} 行, 匹配 {} 条, 跳过 {} 条, 共发现问题 {} 个",
                    source.getName(), totalRead, totalParsed, totalSkipped, foundIssueIds.size());

        } catch (Exception e) {
            log.error("全量扫描 [{}] 发生错误: {}", source.getName(), e.getMessage(), e);
            progress.setStatus("IDLE");
        }

        // 全量扫描完成后，切换到增量监听模式继续追踪新日志
        if (task.running) {
            task.fullScanMode = false;
            tail(source, task);
        }
    }

    // ========== 全量扫描 ==========

    /**
     * 解析并处理一行原始日志，按级别过滤并传给分析引擎。
     *
     * @return 匹配到的 Issue（新建或已有），跳过返回 null
     */
    private Issue processLine(String rawLine, LogSource source) {
        if (rawLine == null || rawLine.trim().isEmpty()) {
            return null;
        }

        // 解码 UTF-8（RandomAccessFile.readLine 按 ISO-8859-1 读取，需转回 UTF-8）
        rawLine = new String(rawLine.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);

        // 优先按日志源配置的格式类型匹配解析器
        ParsedLogEntry parsed = null;
        LogParser usedParser = null;
        for (LogParser parser : parsers) {
            if (parser.getFormatType().equalsIgnoreCase(source.getFormatType())) {
                parsed = parser.parse(rawLine);
                usedParser = parser;
                break;
            }
        }
        // 兜底：依次尝试所有解析器
        if (parsed == null) {
            for (LogParser parser : parsers) {
                parsed = parser.parse(rawLine);
                if (parsed != null) {
                    usedParser = parser;
                    break;
                }
            }
        }

        if (parsed == null) {
            return null;
        }

        // 过滤：仅保留 ERROR/WARN/CRITICAL/FATAL 等需要关注的日志级别
        String level = parsed.getLevel();
        if (!"ERROR".equalsIgnoreCase(level) && !"WARN".equalsIgnoreCase(level)
                && !"CRITICAL".equalsIgnoreCase(level) && !"FATAL".equalsIgnoreCase(level)
                && !"EMERGENCY".equalsIgnoreCase(level) && !"ALERT".equalsIgnoreCase(level)) {
            return null;
        }

        // 堆栈追踪续行：追加到最近一个 Issue 的日志列表中，避免创建重复问题
        if (STACK_TRACE.matcher(rawLine).find()) {
            Issue lastIssue = lastIssues.get(source.getId());
            if (lastIssue != null) {
                logAggregator.saveLogEntry(parsed, source.getName(), lastIssue);
                // 仅更新最后出现时间，不递增出现次数——堆栈续行属于同一次异常事件
                lastIssue.setLastSeen(LocalDateTime.now());
                issueRepository.save(lastIssue);
                return lastIssue;
            }
            // 还没有上一个 Issue——当作普通 ERROR 处理（理论上不会走到这里）
        } else {
            // 非堆栈行——清除上一次的问题上下文
            lastIssues.remove(source.getId());
        }

        // 传入分析引擎（规则匹配 → 风险评分 → 聚合入库）
        AnalysisEngine.AnalysisResult result = analysisEngine.analyze(parsed, source.getName());

        if (result.getIssue() != null) {
            // 记住本轮问题，供后续堆栈行关联
            lastIssues.put(source.getId(), result.getIssue());

            // 前 5 个新问题：使用 INFO 级别日志便于用户确认系统正常运行
            if (newIssueLogCount.incrementAndGet() <= 5) {
                String msg = parsed.getMessage();
                if (msg != null && msg.length() > 120) msg = msg.substring(0, 120) + "...";
                log.info("新增问题 #{} | 级别:{} 风险:{} 评分:{} | {} | 解析器:{}",
                        result.getIssue().getId(), level, result.getIssue().getRiskLevel(),
                        result.getIssue().getRiskScore(), msg,
                        usedParser != null ? usedParser.getClass().getSimpleName() : "unknown");
            }
            log.debug("{} 日志已分析 | 解析器:{} | 问题:#{} | 级别:{} | 评分:{} | 需AI:{}",
                    level, usedParser != null ? usedParser.getClass().getSimpleName() : "unknown",
                    result.getIssue().getId(), result.getIssue().getRiskLevel(),
                    result.getIssue().getRiskScore(), result.isNeedAiAnalysis());

            return result.getIssue();
        }
        return null;
    }

    // ========== 日志行处理 ==========

    private long getFileInode(File file) {
        try {
            BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            Object fileKey = attr.fileKey();
            return fileKey != null ? fileKey.hashCode() : file.lastModified();
        } catch (Exception e) {
            return file.lastModified();
        }
    }

    // ========== 工具方法 ==========

    private long countLines(File file) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long count = 0;
            while (raf.readLine() != null) {
                count++;
            }
            return count;
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("FileTailerService 正在关闭...");
        activeTasks.values().forEach(TailTask::stop);
        activeTasks.clear();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Getter
    public static class TailTask {
        private final Long sourceId;
        private volatile boolean running = true;
        private volatile boolean fullScanMode = false;

        public TailTask(Long sourceId) {
            this.sourceId = sourceId;
        }

        public void stop() {
            running = false;
        }
    }
}
