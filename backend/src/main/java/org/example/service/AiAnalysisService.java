package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ai.AiAnalyzer;
import org.example.entity.AiAnalysisStatus;
import org.example.entity.Issue;
import org.example.repository.IssueRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 分析调度服务 —— 定时扫描 PENDING 状态的问题并提交给 AI 分析器。
 * <p>
 * 每 30 秒运行一次，每次最多处理 5 个问题，避免触发频率限制。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private final IssueRepository issueRepository;
    private final AiAnalyzer aiAnalyzer;

    /**
     * 处理待分析问题，每 30 秒执行一次，每次最多处理 5 个。
     */
    @Scheduled(fixedDelay = 30_000)
    public void processPendingAnalyses() {
        List<Issue> pending = issueRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("aiAnalysisStatus"), AiAnalysisStatus.PENDING),
                PageRequest.of(0, 5)
        ).getContent();

        if (pending.isEmpty()) {
            return;
        }

        log.info("处理 {} 个待分析问题", pending.size());
        int success = 0;
        int failed = 0;
        int deferred = 0;

        for (Issue issue : pending) {
            try {
                boolean result = aiAnalyzer.analyze(issue);
                if (result) {
                    success++;
                } else {
                    deferred++;
                }
            } catch (Exception e) {
                log.error("问题 #{} AI 分析异常: {}", issue.getId(), e.getMessage());
                // 发生异常时标记为失败
                issue.setAiAnalysisStatus(AiAnalysisStatus.FAILED);
                issueRepository.save(issue);
                failed++;
            }
        }

        log.info("AI 分析批次完成: 成功={}, 延期={}, 失败={}", success, deferred, failed);
    }
}
