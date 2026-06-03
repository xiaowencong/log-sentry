package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.collector.FileTailerService;
import org.example.dto.ScanProgressDTO;
import org.example.entity.LogSource;
import org.example.entity.ScanMode;
import org.example.repository.LogSourceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogSourceService {

    private final LogSourceRepository logSourceRepository;
    private final FileTailerService fileTailerService;

    public List<LogSource> listAll() {
        return logSourceRepository.findAll();
    }

    public List<LogSource> listEnabled() {
        return logSourceRepository.findByEnabledTrue();
    }

    public LogSource getById(Long id) {
        return logSourceRepository.findById(id).orElse(null);
    }

    public LogSource create(LogSource source) {
        // 新建默认增量模式——全量扫描需手动触发
        source.setScanMode(ScanMode.INCREMENTAL);
        LogSource saved = logSourceRepository.save(source);
        // 启用后自动开始监听
        if (saved.getEnabled()) {
            fileTailerService.start(saved.getId());
        }
        return saved;
    }

    public LogSource update(Long id, LogSource source) {
        LogSource existing = logSourceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Log source not found: " + id));

        // Stop existing tailing if was running
        if (fileTailerService.isActive(id)) {
            fileTailerService.stop(id);
        }

        existing.setName(source.getName());
        existing.setSourceType(source.getSourceType());
        existing.setPath(source.getPath());
        existing.setFormatType(source.getFormatType());
        // Only update scanMode if explicitly provided
        if (source.getScanMode() != null) {
            existing.setScanMode(source.getScanMode());
        }
        existing.setBatchSize(source.getBatchSize());
        existing.setBatchIntervalMs(source.getBatchIntervalMs());
        existing.setParserConfig(source.getParserConfig());
        existing.setEnabled(source.getEnabled());

        LogSource saved = logSourceRepository.save(existing);

        // 更新后如果仍启用则重新开始监听
        if (saved.getEnabled()) {
            fileTailerService.start(saved.getId());
        }

        return saved;
    }

    public void delete(Long id) {
        fileTailerService.stop(id);
        logSourceRepository.deleteById(id);
    }

    public void toggleEnabled(Long id) {
        LogSource source = getById(id);
        if (source != null) {
            source.setEnabled(!source.getEnabled());
            logSourceRepository.save(source);

            if (source.getEnabled()) {
                fileTailerService.start(id);
            } else {
                fileTailerService.stop(id);
            }
        }
    }

    public void triggerFullScan(Long id) {
        fileTailerService.triggerFullScan(id);
        LogSource source = getById(id);
        if (source != null) {
            source.setLastCollectTime(LocalDateTime.now());
            logSourceRepository.save(source);
        }
    }

    public ScanProgressDTO getScanProgress(Long id) {
        return fileTailerService.getScanProgress(id);
    }

    /**
     * 应用启动时自动启动所有启用的日志源。
     */
    public void startAllEnabled() {
        List<LogSource> sources = logSourceRepository.findByEnabledTrue();
        log.info("启动 {} 个已启用的日志源...", sources.size());
        for (LogSource source : sources) {
            try {
                fileTailerService.start(source.getId());
            } catch (Exception e) {
                log.error("启动日志源 {} 失败: {}", source.getId(), e.getMessage());
            }
        }
    }
}
