package org.example.repository;

import org.example.entity.AnalysisRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisRuleRepository extends JpaRepository<AnalysisRule, Long> {
    List<AnalysisRule> findByEnabledTrue();

    List<AnalysisRule> findByEnabledTrueAndIsBuiltinTrue();
}
