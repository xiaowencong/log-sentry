package org.example.repository;

import org.example.entity.AiAnalysisStatus;
import org.example.entity.Issue;
import org.example.entity.IssueStatus;
import org.example.entity.RiskLevel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IssueRepository extends JpaRepository<Issue, Long>, JpaSpecificationExecutor<Issue> {
    Optional<Issue> findByFingerprint(String fingerprint);

    long countByRiskLevel(RiskLevel riskLevel);

    long countByRiskLevelAndStatusNot(RiskLevel riskLevel, IssueStatus status);

    long countByStatus(IssueStatus status);

    /**
     * 查找同分类下已有 AI 分析结果的问题（用于复用分析，避免重复调用 API）。
     */
    @Query("SELECT i FROM Issue i WHERE i.category = :category AND i.aiAnalysisStatus = :status AND i.id <> :excludeId ORDER BY i.lastSeen DESC")
    List<Issue> findCompletedByCategory(@Param("category") String category,
                                        @Param("status") AiAnalysisStatus status,
                                        @Param("excludeId") Long excludeId,
                                        Pageable pageable);
}
