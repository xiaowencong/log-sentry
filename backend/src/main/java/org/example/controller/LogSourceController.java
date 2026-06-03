package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.common.Result;
import org.example.dto.ScanProgressDTO;
import org.example.entity.LogSource;
import org.example.service.LogSourceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sources")
@RequiredArgsConstructor
public class LogSourceController {

    private final LogSourceService logSourceService;

    @GetMapping
    public Result<List<LogSource>> list() {
        return Result.success(logSourceService.listAll());
    }

    @GetMapping("/{id}")
    public Result<LogSource> getById(@PathVariable Long id) {
        return Result.success(logSourceService.getById(id));
    }

    @PostMapping
    public Result<LogSource> create(@RequestBody LogSource source) {
        return Result.success(logSourceService.create(source));
    }

    @PutMapping("/{id}")
    public Result<LogSource> update(@PathVariable Long id, @RequestBody LogSource source) {
        return Result.success(logSourceService.update(id, source));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        logSourceService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/toggle")
    public Result<Void> toggleEnabled(@PathVariable Long id) {
        logSourceService.toggleEnabled(id);
        return Result.success();
    }

    @PostMapping("/{id}/full-scan")
    public Result<Void> triggerFullScan(@PathVariable Long id) {
        logSourceService.triggerFullScan(id);
        return Result.success();
    }

    @GetMapping("/{id}/scan-progress")
    public Result<ScanProgressDTO> getScanProgress(@PathVariable Long id) {
        return Result.success(logSourceService.getScanProgress(id));
    }
}
