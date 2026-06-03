package org.example.collector.parser;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logback/Log4j pattern parser.
 * Default Logback pattern: %d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n
 * Also supports Log4j patterns.
 */
@Component
public class LogbackPatternParser implements LogParser {

    // Pattern: timestamp [thread] LEVEL logger - message
    private static final Pattern LOGBACK_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}[.,]\\d{3})\\s*" +
                    "\\[([^\\]]*)\\]\\s*" +
                    "(\\w+)\\s*" +
                    "(\\S+)\\s*-\\s*" +
                    "(.*)$"
    );

    // Log4j pattern: timestamp LEVEL [thread] logger - message
    private static final Pattern LOG4J_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}[.,]\\d{3})\\s*" +
                    "(\\w+)\\s*" +
                    "\\[([^\\]]*)\\]\\s*" +
                    "(\\S+)\\s*-\\s*" +
                    "(.*)$"
    );

    private static final DateTimeFormatter[] FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"),
    };

    @Override
    public ParsedLogEntry parse(String rawLine) {
        if (rawLine == null || rawLine.trim().isEmpty()) {
            return null;
        }

        // Try Logback pattern first
        Matcher logbackMatcher = LOGBACK_PATTERN.matcher(rawLine);
        if (logbackMatcher.matches()) {
            return buildEntry(logbackMatcher, rawLine, 1, 3, 4, 5);
        }

        // Try Log4j pattern
        Matcher log4jMatcher = LOG4J_PATTERN.matcher(rawLine);
        if (log4jMatcher.matches()) {
            return buildEntry(log4jMatcher, rawLine, 1, 2, 4, 5);
        }

        return null;
    }

    private ParsedLogEntry buildEntry(Matcher m, String rawLine,
                                      int timeGroup, int levelGroup, int loggerGroup, int msgGroup) {
        ParsedLogEntry entry = new ParsedLogEntry();
        entry.setRawLine(rawLine);
        entry.setTimestamp(parseTime(m.group(timeGroup)));
        entry.setLevel(m.group(levelGroup).toUpperCase());
        entry.setMessage(m.group(msgGroup) != null ? m.group(msgGroup).trim() : "");

        String logger = m.group(loggerGroup);
        if (logger != null) {
            // 取 logger 名中最后一个有意义的部分作为服务名
            // 例如 com.example.order.service → order
            entry.setServiceName(extractServiceName(logger));
            entry.getMetadata().put("logger", logger);
        }

        return entry;
    }

    private LocalDateTime parseTime(String timeStr) {
        for (DateTimeFormatter fmt : FORMATTERS) {
            try {
                return LocalDateTime.parse(timeStr, fmt);
            } catch (Exception ignored) {
            }
        }
        return LocalDateTime.now();
    }

    /**
     * 从完整 logger 名中提取有意义的服务名。
     * 策略：去掉 TLD 级前缀（com/org/net/cn），取接下来两个有效段。
     * 例如 com.example.order.service → example.order，com.example → example
     */
    private String extractServiceName(String logger) {
        String[] parts = logger.split("\\.");
        if (parts.length <= 1) return logger;
        // 跳过常见的顶级域名段
        int start = 0;
        if (parts[0].length() <= 3 && parts.length > 1) {
            start = 1;
        }
        if (start >= parts.length) return logger;
        // 取最多两段作为服务名
        int end = Math.min(start + 2, parts.length);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (sb.length() > 0) sb.append('.');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    @Override
    public String getFormatType() {
        return "LOGBACK";
    }
}
