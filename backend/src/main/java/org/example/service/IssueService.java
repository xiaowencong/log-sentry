package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.entity.*;
import org.example.repository.AiAnalysisRepository;
import org.example.repository.IssueRepository;
import org.example.repository.LogEntryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IssueService {

    private final IssueRepository issueRepository;
    private final LogEntryRepository logEntryRepository;
    private final AiAnalysisRepository aiAnalysisRepository;

    public Page<Issue> queryIssues(String riskLevel, String status, String serviceName,
                                   String keyword, int page, int size) {
        Specification<Issue> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (riskLevel != null && !riskLevel.isEmpty()) {
                predicates.add(cb.equal(root.get("riskLevel"), RiskLevel.valueOf(riskLevel)));
            }
            if (status != null && !status.isEmpty()) {
                predicates.add(cb.equal(root.get("status"), IssueStatus.valueOf(status)));
            }
            if (serviceName != null && !serviceName.isEmpty()) {
                predicates.add(cb.like(root.get("serviceName"), "%" + serviceName + "%"));
            }
            if (keyword != null && !keyword.isEmpty()) {
                predicates.add(cb.or(
                        cb.like(root.get("summary"), "%" + keyword + "%"),
                        cb.like(root.get("source"), "%" + keyword + "%")
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return issueRepository.findAll(spec,
                PageRequest.of(page - 1, size, Sort.by("lastSeen").descending()));
    }

    public Issue getById(Long id) {
        return issueRepository.findById(id).orElse(null);
    }

    public Issue updateStatus(Long id, IssueStatus status) {
        Issue issue = issueRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Issue not found: " + id));
        issue.setStatus(status);
        return issueRepository.save(issue);
    }

    public AiAnalysis getAiAnalysis(Long issueId) {
        return aiAnalysisRepository.findByIssueId(issueId).orElse(null);
    }

    public AiAnalysis updateFeedback(Long issueId, Integer score) {
        AiAnalysis analysis = aiAnalysisRepository.findByIssueId(issueId)
                .orElseThrow(() -> new RuntimeException("AI analysis not found for issue: " + issueId));
        analysis.setFeedbackScore(score);
        return aiAnalysisRepository.save(analysis);
    }

    public List<LogEntry> getIssueLogs(Long issueId) {
        return logEntryRepository.findByIssueIdOrderByTimestampAsc(issueId);
    }
}
