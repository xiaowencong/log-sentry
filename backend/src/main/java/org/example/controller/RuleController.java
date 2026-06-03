package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.common.Result;
import org.example.entity.AnalysisRule;
import org.example.service.RuleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
public class RuleController {

    private final RuleService ruleService;

    @GetMapping
    public Result<List<AnalysisRule>> list() {
        return Result.success(ruleService.listAll());
    }

    @GetMapping("/{id}")
    public Result<AnalysisRule> getById(@PathVariable Long id) {
        return Result.success(ruleService.getById(id));
    }

    @PostMapping
    public Result<AnalysisRule> create(@RequestBody AnalysisRule rule) {
        return Result.success(ruleService.create(rule));
    }

    @PutMapping("/{id}")
    public Result<AnalysisRule> update(@PathVariable Long id, @RequestBody AnalysisRule rule) {
        return Result.success(ruleService.update(id, rule));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ruleService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/toggle")
    public Result<Void> toggleEnabled(@PathVariable Long id) {
        ruleService.toggleEnabled(id);
        return Result.success();
    }
}
