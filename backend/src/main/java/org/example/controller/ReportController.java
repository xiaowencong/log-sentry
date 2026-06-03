package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.common.Result;
import org.example.entity.Report;
import org.example.service.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping
    public Result<List<Report>> list(@RequestParam(required = false) String type) {
        if (type != null && !type.isEmpty()) {
            return Result.success(reportService.listByType(type));
        }
        return Result.success(reportService.listAll());
    }

    @GetMapping("/{id}")
    public Result<Report> getById(@PathVariable Long id) {
        return Result.success(reportService.getById(id));
    }

    @PostMapping("/generate/daily")
    public Result<Report> generateDaily(@RequestBody(required = false) Map<String, String> params) {
        if (params != null) {
            return Result.success(reportService.generateCustom(
                    LocalDateTime.now().toLocalDate().atStartOfDay(), LocalDateTime.now(),
                    params.get("riskLevel"), params.get("status"),
                    params.get("category"), params.get("keyword")));
        }
        return Result.success(reportService.generateDaily());
    }

    @PostMapping("/generate/weekly")
    public Result<Report> generateWeekly(@RequestBody(required = false) Map<String, String> params) {
        if (params != null && hasAnyFilter(params)) {
            LocalDateTime now = LocalDateTime.now();
            return Result.success(reportService.generateCustom(
                    now.minusDays(7), now,
                    params.get("riskLevel"), params.get("status"),
                    params.get("category"), params.get("keyword")));
        }
        return Result.success(reportService.generateWeekly());
    }

    @PostMapping("/generate/monthly")
    public Result<Report> generateMonthly(@RequestBody(required = false) Map<String, String> params) {
        if (params != null && hasAnyFilter(params)) {
            LocalDateTime now = LocalDateTime.now();
            return Result.success(reportService.generateCustom(
                    now.minusDays(30), now,
                    params.get("riskLevel"), params.get("status"),
                    params.get("category"), params.get("keyword")));
        }
        return Result.success(reportService.generateMonthly());
    }

    @PostMapping("/generate/custom")
    public Result<Report> generateCustom(@RequestBody Map<String, String> params) {
        String startStr = params.get("startTime");
        String endStr = params.get("endTime");
        LocalDateTime start = startStr != null ? LocalDateTime.parse(startStr) : LocalDateTime.now().minusDays(1);
        LocalDateTime end = endStr != null ? LocalDateTime.parse(endStr) : LocalDateTime.now();
        return Result.success(reportService.generateCustom(start, end,
                params.get("riskLevel"), params.get("status"),
                params.get("category"), params.get("keyword")));
    }

    private boolean hasAnyFilter(Map<String, String> params) {
        return (params.get("riskLevel") != null && !params.get("riskLevel").isEmpty())
                || (params.get("status") != null && !params.get("status").isEmpty())
                || (params.get("category") != null && !params.get("category").isEmpty())
                || (params.get("keyword") != null && !params.get("keyword").isEmpty());
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        reportService.delete(id);
        return Result.success();
    }

    /**
     * 下载报表为 .md 文件
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        Report report = reportService.getById(id);
        if (report == null || report.getContent() == null) {
            return ResponseEntity.notFound().build();
        }
        String filename = (report.getTitle() != null ? report.getTitle() : "report") + ".md";
        String encodedFilename = null;
        try {
            encodedFilename = URLEncoder.encode(filename, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFilename)
                .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
                .body(report.getContent().getBytes(StandardCharsets.UTF_8));
    }
}
