package org.example.analyzer;

import org.example.entity.AnalysisRule;
import org.example.entity.RiskLevel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 风险评分器 —— 基于匹配规则、日志级别和出现频率计算风险评分。
 * <p>
 * 评分逻辑：规则得分（70%）+ 级别基础分（30%），乘以频率系数（1.0~1.3x）。
 */
@Component
public class RiskScorer {

    /**
     * 各日志级别的基础风险分
     */
    private static final int LEVEL_ERROR_BASE = 60;
    private static final int LEVEL_WARN_BASE = 35;
    private static final int LEVEL_FATAL_BASE = 85;
    private static final int LEVEL_CRITICAL_BASE = 90;

    /**
     * 计算最终的风险评分和等级。
     *
     * @param matchingRules   匹配到的规则列表
     * @param logLevel        日志级别（ERROR / WARN 等）
     * @param occurrenceCount 该问题在时间窗口内的出现次数
     * @return 包含 riskLevel 和 riskScore 的评分结果
     */
    public ScoredResult compute(List<AnalysisRule> matchingRules, String logLevel, int occurrenceCount) {
        int ruleScore = 0;
        RiskLevel ruleLevel = null;

        // 从匹配规则中取最高风险评分
        if (matchingRules != null && !matchingRules.isEmpty()) {
            for (AnalysisRule rule : matchingRules) {
                int rs = rule.getRiskScore() != null ? rule.getRiskScore() : 0;
                if (rs > ruleScore) {
                    ruleScore = rs;
                    ruleLevel = rule.getRiskLevel();
                }
            }
        }

        // 日志级别基础分
        int levelBase = getLevelBase(logLevel);

        // 综合评分：规则得分 70% + 级别基础分 30%（未匹配规则时仅用级别分）
        double combinedScore;
        if (ruleScore > 0) {
            combinedScore = ruleScore * 0.7 + levelBase * 0.3;
        } else {
            combinedScore = levelBase;
        }

        // 频率系数：出现次数越多，风险越高
        // 1-5 次: 1.0x; 6-20 次: 1.1x; 21-50 次: 1.2x; 51+ 次: 1.3x
        double freqMultiplier;
        if (occurrenceCount <= 5) {
            freqMultiplier = 1.0;
        } else if (occurrenceCount <= 20) {
            freqMultiplier = 1.1;
        } else if (occurrenceCount <= 50) {
            freqMultiplier = 1.2;
        } else {
            freqMultiplier = 1.3;
        }

        int finalScore = (int) Math.min(100, Math.round(combinedScore * freqMultiplier));

        return new ScoredResult(mapScoreToLevel(finalScore), finalScore);
    }

    private int getLevelBase(String level) {
        if (level == null) return LEVEL_ERROR_BASE;
        switch (level.toUpperCase()) {
            case "FATAL":
            case "EMERGENCY":
            case "ALERT":
                return LEVEL_FATAL_BASE;
            case "CRITICAL":
                return LEVEL_CRITICAL_BASE;
            case "ERROR":
                return LEVEL_ERROR_BASE;
            case "WARN":
            case "WARNING":
                return LEVEL_WARN_BASE;
            default:
                return LEVEL_ERROR_BASE;
        }
    }

    private RiskLevel mapScoreToLevel(int score) {
        if (score >= 90) return RiskLevel.CRITICAL;
        if (score >= 70) return RiskLevel.HIGH;
        if (score >= 40) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }

    /**
     * 评分结果封装。
     */
    public static class ScoredResult {
        private final RiskLevel riskLevel;
        private final int riskScore;

        public ScoredResult(RiskLevel riskLevel, int riskScore) {
            this.riskLevel = riskLevel;
            this.riskScore = riskScore;
        }

        public RiskLevel getRiskLevel() {
            return riskLevel;
        }

        public int getRiskScore() {
            return riskScore;
        }
    }
}
