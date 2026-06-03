package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.Report;
import org.example.report.ReportGenerator;
import org.example.repository.ReportRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final ReportGenerator reportGenerator;

    public List<Report> listAll() {
        return reportRepository.findAll();
    }

    public List<Report> listByType(String type) {
        return reportRepository.findByTypeOrderByCreateTimeDesc(type);
    }

    public Report getById(Long id) {
        return reportRepository.findById(id).orElse(null);
    }

    public Report generateDaily() {
        Report report = reportGenerator.generateDaily();
        return reportRepository.save(report);
    }

    public Report generateWeekly() {
        Report report = reportGenerator.generateWeekly();
        return reportRepository.save(report);
    }

    public Report generateMonthly() {
        Report report = reportGenerator.generateMonthly();
        return reportRepository.save(report);
    }

    public Report generateCustom(LocalDateTime start, LocalDateTime end,
                                 String riskLevel, String status, String category, String keyword) {
        String title = "自定义报告 - " + start.toLocalDate() + " ~ " + end.toLocalDate();
        Report report = reportGenerator.generate("CUSTOM", title, start, end,
                riskLevel, status, category, keyword);
        return reportRepository.save(report);
    }

    public void delete(Long id) {
        reportRepository.deleteById(id);
    }
}
