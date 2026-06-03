package org.example.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.analyzer.RuleMatcher;
import org.example.entity.AnalysisRule;
import org.example.entity.RiskLevel;
import org.example.repository.AnalysisRuleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 数据初始化器 —— 首次启动时预置内置分析规则。
 * <p>
 * 以规则名称作为唯一键，已存在的规则会跳过。
 * 初始化完成后将所有启用规则加载到 RuleMatcher 缓存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(0) // Run before StartupRunner
public class DataInitializer implements CommandLineRunner {

    private final AnalysisRuleRepository ruleRepository;
    private final RuleMatcher ruleMatcher;

    @Override
    public void run(String... args) {
        initBuiltinRules();
        loadRulesIntoCache();
    }

    private void initBuiltinRules() {
        List<AnalysisRule> builtinRules = Arrays.asList(
                createRule("OutOfMemoryError",
                        "java\\.lang\\.OutOfMemoryError",
                        RiskLevel.CRITICAL, 95, "系统资源"),
                createRule("ConnectionTimeout",
                        "Connection.*timed?.*out|connect.*timeout",
                        RiskLevel.HIGH, 75, "网络通信"),
                createRule("DatabaseDeadlock",
                        "deadlock.*detected|DeadlockLoserDataAccessException",
                        RiskLevel.HIGH, 80, "数据库"),
                createRule("NullPointerException",
                        "NullPointerException",
                        RiskLevel.HIGH, 72, "代码缺陷"),
                createRule("DiskSpaceLow",
                        "No space left on device|disk.*full",
                        RiskLevel.CRITICAL, 92, "系统资源"),
                createRule("SlowQuery",
                        "SlowQuery|query.*execution.*time|slow.*sql",
                        RiskLevel.MEDIUM, 55, "性能问题"),
                createRule("SocketTimeout",
                        "SocketTimeoutException|read.*timed.*out|connect.*timed.*out",
                        RiskLevel.HIGH, 70, "网络通信"),
                createRule("ClassNotFoundException",
                        "ClassNotFoundException|NoClassDefFoundError",
                        RiskLevel.HIGH, 68, "代码缺陷"),
                createRule("Http500Error",
                        "HTTP.*500|Internal Server Error|status.*500",
                        RiskLevel.HIGH, 73, "服务异常"),
                createRule("ConnectionRefused",
                        "Connection refused|connect.*refused",
                        RiskLevel.HIGH, 78, "网络通信")
        );

        int inserted = 0;
        for (AnalysisRule rule : builtinRules) {
            // Check if rule with this name already exists (built-in rules identified by name)
            boolean exists = ruleRepository.findAll().stream()
                    .anyMatch(r -> rule.getName().equals(r.getName()) && r.getIsBuiltin());
            if (!exists) {
                ruleRepository.save(rule);
                inserted++;
            }
        }

        if (inserted > 0) {
            log.info("已初始化 {} 条内置分析规则", inserted);
        } else {
            log.info("内置规则已存在，跳过初始化");
        }
    }

    private AnalysisRule createRule(String name, String pattern, RiskLevel level, int score, String category) {
        AnalysisRule rule = new AnalysisRule();
        rule.setName(name);
        rule.setPattern(pattern);
        rule.setRiskLevel(level);
        rule.setRiskScore(score);
        rule.setCategory(category);
        rule.setEnabled(true);
        rule.setIsBuiltin(true);
        return rule;
    }

    /**
     * 将所有启用规则加载到 RuleMatcher 内存缓存。
     */
    private void loadRulesIntoCache() {
        List<AnalysisRule> enabledRules = ruleRepository.findByEnabledTrue();
        ruleMatcher.refreshRules(enabledRules);
        log.info("已加载 {} 条规则到匹配器缓存", enabledRules.size());
    }
}
