package org.example.ai;

import lombok.extern.slf4j.Slf4j;
import org.example.entity.AiAnalysis;
import org.example.repository.AiAnalysisRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 分析结果内存缓存 —— 避免重复调用 API。
 * <p>
 * 缓存条目按配置的 TTL 过期（默认 24 小时）。
 * 超过 10000 条时自动清理最旧的 10%。
 */
@Slf4j
@Component
public class AiResultCache {

    private final AiAnalysisRepository aiAnalysisRepository;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    @Value("${log-sentry.ai.cache-hours:24}")
    private int cacheHours;

    public AiResultCache(AiAnalysisRepository aiAnalysisRepository) {
        this.aiAnalysisRepository = aiAnalysisRepository;
    }

    /**
     * 按 Issue 指纹查询缓存或数据库中的已有分析结果。
     * 返回未过期的分析结果，过期则自动移除。
     */
    public AiAnalysis get(String fingerprint) {
        // 检查内存缓存
        CacheEntry entry = cache.get(fingerprint);
        if (entry != null) {
            if (!entry.isExpired(cacheHours)) {
                return entry.analysis;
            }
            cache.remove(fingerprint);
        }

        // 此处不查数据库（无 issueId），由 AiAnalyzer 直接按 issueId 查询
        return null;
    }

    /**
     * 将分析结果放入缓存。
     */
    public void put(String fingerprint, AiAnalysis analysis) {
        cache.put(fingerprint, new CacheEntry(analysis));
        // 限制缓存大小，防止内存泄漏
        if (cache.size() > 10000) {
            // 移除最旧的 10%
            int toRemove = cache.size() / 10;
            cache.keySet().stream().limit(toRemove).forEach(cache::remove);
        }
    }

    /**
     * 使指定指纹的缓存条目失效。
     */
    public void invalidate(String fingerprint) {
        cache.remove(fingerprint);
    }

    private static class CacheEntry {
        final AiAnalysis analysis;
        final LocalDateTime createdAt;

        CacheEntry(AiAnalysis analysis) {
            this.analysis = analysis;
            this.createdAt = LocalDateTime.now();
        }

        boolean isExpired(int hours) {
            return createdAt.plusHours(hours).isBefore(LocalDateTime.now());
        }
    }
}
