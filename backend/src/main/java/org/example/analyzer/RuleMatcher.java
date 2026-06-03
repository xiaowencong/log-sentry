package org.example.analyzer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.AnalysisRule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 规则匹配器 —— 使用正则表达式将日志消息与启用的分析规则进行匹配。
 * <p>
 * 在启动时编译所有启用规则到内存缓存（CompiledRule），
 * 匹配结果按风险评分降序排列。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleMatcher {

    private volatile List<CompiledRule> compiledRules = new ArrayList<>();

    /**
     * 从数据库刷新编译后的规则缓存。
     */
    public void refreshRules(List<AnalysisRule> rules) {
        List<CompiledRule> newCache = new ArrayList<>();
        for (AnalysisRule rule : rules) {
            if (rule.getEnabled() && rule.getPattern() != null && !rule.getPattern().isEmpty()) {
                try {
                    Pattern pattern = Pattern.compile(rule.getPattern(), Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
                    newCache.add(new CompiledRule(rule, pattern));
                } catch (PatternSyntaxException e) {
                    log.warn("规则 '{}' 正则表达式无效: {}", rule.getName(), e.getMessage());
                }
            }
        }
        this.compiledRules = newCache;
        log.info("规则缓存已刷新: 加载 {} 条规则", newCache.size());
    }

    /**
     * 将日志消息与所有启用规则进行匹配。
     *
     * @return 按风险评分降序排列的匹配规则列表
     */
    public List<AnalysisRule> match(String message, String rawLine) {
        List<AnalysisRule> matched = new ArrayList<>();
        String searchText = (message != null ? message : "") + " " + (rawLine != null ? rawLine : "");

        for (CompiledRule cr : compiledRules) {
            Matcher matcher = cr.pattern.matcher(searchText);
            if (matcher.find()) {
                matched.add(cr.rule);
            }
        }

        // 按风险评分降序排列（最高风险在前）
        matched.sort((a, b) -> Integer.compare(
                b.getRiskScore() != null ? b.getRiskScore() : 0,
                a.getRiskScore() != null ? a.getRiskScore() : 0));
        return matched;
    }

    /**
     * 内部封装：预编译的正则表达式 + 原始规则对象。
     */
    private static class CompiledRule {
        final AnalysisRule rule;
        final Pattern pattern;

        CompiledRule(AnalysisRule rule, Pattern pattern) {
            this.rule = rule;
            this.pattern = pattern;
        }
    }
}
