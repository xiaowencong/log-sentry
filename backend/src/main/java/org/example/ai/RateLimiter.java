package org.example.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 简单滑动窗口频率限制器。
 * <p>
 * 限制 DeepSeek API 每分钟调用次数，超过上限则拒绝并延期处理。
 */
@Slf4j
@Component
public class RateLimiter {

    private final AtomicInteger counter = new AtomicInteger(0);
    private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
    @Value("${log-sentry.ai.rate-limit-per-minute:10}")
    private int maxCallsPerMinute;

    /**
     * 尝试获取一个许可。返回 true 表示允许调用，false 表示被限流。
     */
    public synchronized boolean tryAcquire() {
        long now = System.currentTimeMillis();
        long windowStartMs = windowStart.get();

        // 每 60 秒重置一次计数窗口
        if (now - windowStartMs >= 60_000) {
            windowStart.set(now);
            counter.set(0);
        }

        int current = counter.incrementAndGet();
        if (current <= maxCallsPerMinute) {
            return true;
        }

        // 触发限流，回退计数
        counter.decrementAndGet();
        if (current == maxCallsPerMinute + 1) {
            log.warn("触发频率限制: {} 次/分钟", maxCallsPerMinute);
        }
        return false;
    }

    /**
     * 估算下次可调用的等待秒数。
     */
    public long getWaitSeconds() {
        long now = System.currentTimeMillis();
        long elapsed = now - windowStart.get();
        if (elapsed >= 60_000) return 0;
        return (60_000 - elapsed) / 1000 + 1;
    }
}
