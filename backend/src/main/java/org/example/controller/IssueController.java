package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.common.Result;
import org.example.dto.UpdateStatusDTO;
import org.example.entity.AiAnalysis;
import org.example.entity.Issue;
import org.example.entity.IssueStatus;
import org.example.entity.LogEntry;
import org.example.service.IssueService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;

    @GetMapping
    public Result<Map<String, Object>> query(
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<Issue> result = issueService.queryIssues(riskLevel, status, serviceName, keyword, page, size);
        Map<String, Object> data = new HashMap<>();
        data.put("total", result.getTotalElements());
        data.put("page", page);
        data.put("items", result.getContent());
        return Result.success(data);
    }

    @GetMapping("/{id}")
    public Result<Issue> getDetail(@PathVariable Long id) {
        return Result.success(issueService.getById(id));
    }

    @PutMapping("/{id}/status")
    public Result<Issue> updateStatus(@PathVariable Long id, @RequestBody UpdateStatusDTO dto) {
        IssueStatus status = IssueStatus.valueOf(dto.getStatus());
        return Result.success(issueService.updateStatus(id, status));
    }

    @GetMapping("/{id}/logs")
    public Result<List<LogEntry>> getLogs(@PathVariable Long id) {
        return Result.success(issueService.getIssueLogs(id));
    }

    @GetMapping("/{id}/ai-analysis")
    public Result<AiAnalysis> getAiAnalysis(@PathVariable Long id) {
        return Result.success(issueService.getAiAnalysis(id));
    }

    @PutMapping("/{id}/ai-feedback")
    public Result<AiAnalysis> updateFeedback(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer score = body.get("score");
        return Result.success(issueService.updateFeedback(id, score));
    }
}
