package org.example.collector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.FilePosition;
import org.example.repository.FilePositionRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 文件读取偏移量追踪器 —— 管理日志文件的读取进度。
 * <p>
 * 将偏移量持久化到数据库，使系统重启后能从断点继续监听。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FilePositionTracker {

    private final FilePositionRepository filePositionRepository;

    /**
     * 获取文件的上次读取偏移量，从未读取返回 0。
     */
    public long getLastOffset(Long sourceId, String filePath) {
        return filePositionRepository.findBySourceIdAndFilePath(sourceId, filePath)
                .map(FilePosition::getLastOffset)
                .orElse(0L);
    }

    /**
     * 保存或更新文件的读取位置。
     */
    public void savePosition(Long sourceId, String filePath, long offset, long inode) {
        FilePosition pos = filePositionRepository.findBySourceIdAndFilePath(sourceId, filePath)
                .orElseGet(() -> {
                    FilePosition newPos = new FilePosition();
                    newPos.setSourceId(sourceId);
                    newPos.setFilePath(filePath);
                    return newPos;
                });

        pos.setLastOffset(offset);
        pos.setLastInode(inode);
        pos.setLastReadTime(LocalDateTime.now());
        filePositionRepository.save(pos);
    }

    /**
     * 重置读取位置到 0（用于全量重新扫描）。
     * 若不存在记录则新建。
     */
    public void resetPosition(Long sourceId, String filePath) {
        FilePosition pos = filePositionRepository.findBySourceIdAndFilePath(sourceId, filePath)
                .orElseGet(() -> {
                    FilePosition newPos = new FilePosition();
                    newPos.setSourceId(sourceId);
                    newPos.setFilePath(filePath);
                    return newPos;
                });
        pos.setLastOffset(0L);
        pos.setLastInode(null);
        pos.setLastReadTime(LocalDateTime.now());
        filePositionRepository.save(pos);
        log.info("偏移量已重置: sourceId={} path={}", sourceId, filePath);
    }

    /**
     * 获取存储的 inode 值，用于文件轮转检测。
     */
    public Long getLastInode(Long sourceId, String filePath) {
        return filePositionRepository.findBySourceIdAndFilePath(sourceId, filePath)
                .map(FilePosition::getLastInode)
                .orElse(null);
    }
}
