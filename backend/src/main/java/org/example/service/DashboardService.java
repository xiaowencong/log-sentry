package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.DashboardStatsDTO;
import org.example.entity.Issue;
import org.example.entity.RiskLevel;
import org.example.repository.IssueRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final IssueRepository issueRepository;

    public DashboardStatsDTO getStats() {
        DashboardStatsDTO stats = new DashboardStatsDTO();
        // 仅统计未关闭的问题（OPEN / ACKNOWLEDGED），排除已解决/关闭的问题
        stats.setCritical(issueRepository.countByRiskLevelAndStatusNot(RiskLevel.CRITICAL, org.example.entity.IssueStatus.CLOSED));
        stats.setHigh(issueRepository.countByRiskLevelAndStatusNot(RiskLevel.HIGH, org.example.entity.IssueStatus.CLOSED));
        stats.setMedium(issueRepository.countByRiskLevelAndStatusNot(RiskLevel.MEDIUM, org.example.entity.IssueStatus.CLOSED));
        stats.setLow(issueRepository.countByRiskLevelAndStatusNot(RiskLevel.LOW, org.example.entity.IssueStatus.CLOSED));
        stats.setTotal(stats.getCritical() + stats.getHigh() + stats.getMedium() + stats.getLow());
        return stats;
    }

    public List<Issue> getRecentIssues(int limit) {
        return issueRepository.findAll(
                PageRequest.of(0, limit, org.springframework.data.domain.Sort.by("lastSeen").descending())
        ).getContent();
    }
}
