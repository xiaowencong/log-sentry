package org.example.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.*;
import org.example.repository.AiAnalysisRepository;
import org.example.repository.IssueRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import javax.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 报表生成器 —— 按时间范围生成日报/周报/月报，输出 Markdown 格式。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportGenerator {

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final IssueRepository issueRepository;
    private final AiAnalysisRepository aiAnalysisRepository;

    /**
     * 按指定时间范围和筛选条件生成报表。
     *
     * @param type      报表类型 DAILY/WEEKLY/MONTHLY/CUSTOM
     * @param title     报表标题
     * @param start     统计开始时间
     * @param end       统计结束时间
     * @param riskLevel 风险等级筛选（可选）
     * @param status    状态筛选（可选）
     * @param category  分类筛选（可选）
     * @param keyword   关键词搜索（可选，匹配摘要和来源）
     */
    public Report generate(String type, String title, LocalDateTime start, LocalDateTime end,
                           String riskLevel, String status, String category, String keyword) {
        // 构建筛选条件
        Specification<Issue> spec = buildSpec(start, end, riskLevel, status, category, keyword);

        // 按风险等级统计问题数
        long criticalCount = countByRisk(start, end, RiskLevel.CRITICAL, riskLevel, status, category, keyword);
        long highCount = countByRisk(start, end, RiskLevel.HIGH, riskLevel, status, category, keyword);
        long mediumCount = countByRisk(start, end, RiskLevel.MEDIUM, riskLevel, status, category, keyword);
        long lowCount = countByRisk(start, end, RiskLevel.LOW, riskLevel, status, category, keyword);
        long totalCount = criticalCount + highCount + mediumCount + lowCount;

        // 获取全部问题，按风险评分降序排列（严重→轻微）
        List<Issue> allIssues = getAllIssues(start, end, riskLevel, status, category, keyword);

        // 构建 Markdown 内容
        StringBuilder md = new StringBuilder();
        md.append("# ").append(title).append("\n\n");
        md.append("**报告类型**: ").append(type).append("  \n");
        md.append("**时间范围**: ").append(start.format(DF)).append(" ~ ").append(end.format(DF)).append("  \n");
        md.append("**生成时间**: ").append(LocalDateTime.now().format(DF)).append("  \n\n");

        md.append("---\n\n");
        md.append("## 一、问题概览\n\n");
        md.append("| 风险等级 | 数量 |\n");
        md.append("|----------|------|\n");
        md.append("| 🔴 CRITICAL | ").append(criticalCount).append(" |\n");
        md.append("| 🟠 HIGH     | ").append(highCount).append(" |\n");
        md.append("| 🟡 MEDIUM   | ").append(mediumCount).append(" |\n");
        md.append("| 🔵 LOW      | ").append(lowCount).append(" |\n");
        md.append("| **合计**    | **").append(totalCount).append("** |\n\n");

        if (totalCount > 0) {
            double criticalPct = criticalCount * 100.0 / totalCount;
            double highPct = highCount * 100.0 / totalCount;
            md.append(String.format("> CRITICAL 占比: %.1f%%, HIGH 占比: %.1f%%\n\n", criticalPct, highPct));
        }

        md.append("---\n\n");
        md.append("## 二、全部问题列表（按优先级排序）\n\n");

        if (allIssues.isEmpty()) {
            md.append("本周期内无高风险问题。\n\n");
        } else {
            md.append("| # | 问题摘要 | 风险等级 | 服务 | 次数 | 首次出现 |\n");
            md.append("|---|----------|----------|------|------|----------|\n");
            int rank = 1;
            for (Issue issue : allIssues) {
                String summary = issue.getSummary() != null ? issue.getSummary() : "-";
                if (summary.length() > 60) summary = summary.substring(0, 60) + "...";
                md.append("| ").append(rank++).append(" | ")
                        .append(summary).append(" | ")
                        .append(issue.getRiskLevel()).append(" | ")
                        .append(issue.getServiceName() != null ? issue.getServiceName() : "-").append(" | ")
                        .append(issue.getOccurrenceCount()).append(" | ")
                        .append(issue.getFirstSeen() != null ? issue.getFirstSeen().format(DF) : "-")
                        .append(" |\n");
            }
        }

        md.append("\n---\n\n");
        md.append("## 三、AI 分析摘要\n\n");

        int aiCount = 0;
        for (Issue issue : allIssues) {
            AiAnalysis analysis = aiAnalysisRepository.findByIssueId(issue.getId()).orElse(null);
            if (analysis != null && analysis.getSummary() != null) {
                aiCount++;
                md.append("### ").append(aiCount).append(". ").append(issue.getSummary() != null ? issue.getSummary() : "未知问题").append("\n\n");
                md.append("**根因分析**: ").append(analysis.getRootCause() != null ? analysis.getRootCause() : "无").append("\n\n");
                if (analysis.getSuggestions() != null) {
                    md.append("**处理建议**: ").append(analysis.getSuggestions()).append("\n\n");
                }
                md.append("---\n\n");
            }
        }
        if (aiCount == 0) {
            md.append("本周期内无 AI 分析结果。\n\n");
        }

        // 构建 Report 实体
        Report report = new Report();
        report.setType(type);
        report.setTitle(title);
        report.setStartTime(start);
        report.setEndTime(end);
        report.setTotalIssues((int) totalCount);
        report.setCriticalCount((int) criticalCount);
        report.setHighCount((int) highCount);
        report.setMediumCount((int) mediumCount);
        report.setLowCount((int) lowCount);
        report.setContent(md.toString());

        return report;
    }

    /**
     * 生成今天的日报。
     */
    public Report generateDaily() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        return generate("DAILY", "日报 - " + now.toLocalDate(), todayStart, now, null, null, null, null);
    }

    /**
     * 生成最近 7 天的周报。
     */
    public Report generateWeekly() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekStart = now.minusDays(7);
        String title = "周报 - " + weekStart.toLocalDate() + " ~ " + now.toLocalDate();
        return generate("WEEKLY", title, weekStart, now, null, null, null, null);
    }

    /**
     * 生成最近 30 天的月报。
     */
    public Report generateMonthly() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthStart = now.minusDays(30);
        String title = "月报 - " + monthStart.toLocalDate() + " ~ " + now.toLocalDate();
        return generate("MONTHLY", title, monthStart, now, null, null, null, null);
    }

    private long countByRisk(LocalDateTime start, LocalDateTime end, RiskLevel level,
                             String riskLevelFilter, String status, String category, String keyword) {
        return issueRepository.count((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("riskLevel"), level));
            predicates.add(cb.between(root.get("firstSeen"), start, end));
            if (status != null && !status.isEmpty()) {
                predicates.add(cb.equal(root.get("status"), IssueStatus.valueOf(status)));
            }
            if (category != null && !category.isEmpty()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (keyword != null && !keyword.isEmpty()) {
                String pattern = "%" + keyword + "%";
                predicates.add(cb.or(
                        cb.like(root.get("summary"), pattern),
                        cb.like(root.get("source"), pattern),
                        cb.like(root.get("serviceName"), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    private Specification<Issue> buildSpec(LocalDateTime start, LocalDateTime end,
                                           String riskLevel, String status, String category, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.between(root.get("firstSeen"), start, end));
            if (riskLevel != null && !riskLevel.isEmpty()) {
                predicates.add(cb.equal(root.get("riskLevel"), RiskLevel.valueOf(riskLevel)));
            }
            if (status != null && !status.isEmpty()) {
                predicates.add(cb.equal(root.get("status"), IssueStatus.valueOf(status)));
            }
            if (category != null && !category.isEmpty()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (keyword != null && !keyword.isEmpty()) {
                String pattern = "%" + keyword + "%";
                predicates.add(cb.or(
                        cb.like(root.get("summary"), pattern),
                        cb.like(root.get("source"), pattern),
                        cb.like(root.get("serviceName"), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private List<Issue> getAllIssues(LocalDateTime start, LocalDateTime end,
                                     String riskLevel, String status, String category, String keyword) {
        Specification<Issue> spec = buildSpec(start, end, riskLevel, status, category, keyword);
        return issueRepository.findAll(spec,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "riskScore"));
    }
}
