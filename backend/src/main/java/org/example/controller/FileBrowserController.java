package org.example.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.*;

/**
 * 文件浏览 API——前端选择日志文件路径时使用
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
public class FileBrowserController {

    /**
     * 浏览目录，返回文件列表
     *
     * @param path 目录路径，默认 "/"
     */
    @GetMapping("/browse")
    public Result<List<Map<String, Object>>> browse(
            @RequestParam(defaultValue = "/") String path) {

        File dir = new File(path);
        List<Map<String, Object>> items = new ArrayList<>();

        // 如果 path 是文件，返回父目录列表
        if (dir.isFile()) {
            dir = dir.getParentFile();
        }
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return Result.error(400, "目录不存在或无权限访问: " + path);
        }

        // 父目录
        String parentPath = dir.getParent();
        if (parentPath != null) {
            Map<String, Object> parent = new HashMap<>();
            parent.put("name", "..");
            parent.put("path", parentPath);
            parent.put("type", "DIR");
            parent.put("size", 0);
            items.add(parent);
        }

        File[] files = dir.listFiles();
        if (files != null) {
            // 先目录，后文件，按名称排序
            Arrays.sort(files, (a, b) -> {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });

            for (File f : files) {
                if (f.isHidden()) continue;       // 跳过隐藏文件
                if (!f.canRead()) continue;        // 跳过不可读文件

                Map<String, Object> item = new HashMap<>();
                item.put("name", f.getName());
                item.put("path", f.getAbsolutePath());
                item.put("type", f.isDirectory() ? "DIR" : "FILE");
                item.put("size", f.isFile() ? f.length() : 0);
                // 只返回 .log .txt .out 和目录，过滤无关文件减少干扰
                if (f.isFile()) {
                    String name = f.getName().toLowerCase();
                    if (!name.endsWith(".log") && !name.endsWith(".txt")
                            && !name.endsWith(".out") && !name.endsWith(".err")) {
                        continue;
                    }
                }
                items.add(item);
            }
        }

        return Result.success(items);
    }

    /**
     * 获取常用系统日志目录（快捷入口）
     */
    @GetMapping("/shortcuts")
    public Result<List<Map<String, String>>> shortcuts() {
        List<Map<String, String>> list = new ArrayList<>();
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            addShortcut(list, "📁 C 盘根目录", "C:\\");
            addShortcut(list, "📁 D 盘根目录", "D:\\");
            addShortcut(list, "📁 用户目录", System.getProperty("user.home"));
        } else {
            addShortcut(list, "📁 根目录", "/");
            addShortcut(list, "📁 /var/log", "/var/log");
            addShortcut(list, "📁 /opt/logs", "/opt/logs");
            addShortcut(list, "📁 用户目录", System.getProperty("user.home"));
            addShortcut(list, "📁 /tmp", "/tmp");
        }

        return Result.success(list);
    }

    private void addShortcut(List<Map<String, String>> list, String name, String path) {
        if (new File(path).exists()) {
            Map<String, String> item = new HashMap<>();
            item.put("name", name);
            item.put("path", path);
            list.add(item);
        }
    }
}
