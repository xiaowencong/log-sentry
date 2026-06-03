package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.analyzer.RuleMatcher;
import org.example.entity.AnalysisRule;
import org.example.repository.AnalysisRuleRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleService {

    private final AnalysisRuleRepository ruleRepository;
    private final RuleMatcher ruleMatcher;

    public List<AnalysisRule> listAll() {
        return ruleRepository.findAll();
    }

    public List<AnalysisRule> listEnabled() {
        return ruleRepository.findByEnabledTrue();
    }

    public AnalysisRule getById(Long id) {
        return ruleRepository.findById(id).orElse(null);
    }

    public AnalysisRule create(AnalysisRule rule) {
        rule.setIsBuiltin(false); // Custom rules are never built-in
        AnalysisRule saved = ruleRepository.save(rule);
        refreshCache();
        return saved;
    }

    public AnalysisRule update(Long id, AnalysisRule rule) {
        AnalysisRule existing = ruleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rule not found: " + id));

        existing.setName(rule.getName());
        existing.setPattern(rule.getPattern());
        existing.setRiskLevel(rule.getRiskLevel());
        existing.setRiskScore(rule.getRiskScore());
        existing.setCategory(rule.getCategory());
        existing.setEnabled(rule.getEnabled());

        AnalysisRule saved = ruleRepository.save(existing);
        refreshCache();
        return saved;
    }

    public void delete(Long id) {
        AnalysisRule rule = getById(id);
        if (rule != null && rule.getIsBuiltin()) {
            throw new RuntimeException("Cannot delete built-in rules");
        }
        ruleRepository.deleteById(id);
        refreshCache();
    }

    public void toggleEnabled(Long id) {
        AnalysisRule rule = getById(id);
        if (rule != null) {
            rule.setEnabled(!rule.getEnabled());
            ruleRepository.save(rule);
            refreshCache();
        }
    }

    /**
     * 规则变更后将所有启用规则重新加载到 RuleMatcher 缓存。
     */
    private void refreshCache() {
        ruleMatcher.refreshRules(ruleRepository.findByEnabledTrue());
    }
}
