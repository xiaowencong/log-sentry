package org.example.collector.parser;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plain text log parser.
 * Supports common patterns like:
 * 2026-06-02 10:30:00 [ERROR] [order-service] Something went wrong
 * 2026-06-02 10:30:00 ERROR order-service - Something went wrong
 * Also detects multi-line stack traces (indented lines with "at ", "Caused by:", etc.)
 */
@Component
public class PlainTextParser implements LogParser {

    // Pattern: timestamp [LEVEL] [service] message
    private static final Pattern PATTERN_BRACKET = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{3})?)\\s*" +
                    "\\[(\\w+)\\]\\s*" +
                    "(?:\\[([^\\]]+)\\]\\s*)?" +
                    "(.*)$"
    );

    // Pattern: timestamp LEVEL service - message
    private static final Pattern PATTERN_DASH = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{3})?)\\s*" +
                    "(\\w+)\\s*" +
                    "([^-\\s]+)?\\s*-\\s*" +
                    "(.*)$"
    );

    // Stack trace continuation line: starts with whitespace, contains at / Caused by / Suppressed / ... N more
    private static final Pattern STACK_TRACE_LINE = Pattern.compile(
            "^\\s+(?:at\\s+|Caused\\s+by:|Suppressed:|...\\s+\\d+\\s+more)");

    private static final DateTimeFormatter[] FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
    };

    @Override
    public ParsedLogEntry parse(String rawLine) {
        if (rawLine == null || rawLine.trim().isEmpty()) {
            return null;
        }

        // Try bracket pattern first
        Matcher bracketMatcher = PATTERN_BRACKET.matcher(rawLine);
        if (bracketMatcher.matches()) {
            return buildEntry(bracketMatcher.group(1), bracketMatcher.group(2),
                    bracketMatcher.group(3), bracketMatcher.group(4), rawLine);
        }

        // Try dash pattern
        Matcher dashMatcher = PATTERN_DASH.matcher(rawLine);
        if (dashMatcher.matches()) {
            return buildEntry(dashMatcher.group(1), dashMatcher.group(2),
                    dashMatcher.group(3), dashMatcher.group(4), rawLine);
        }

        // Fallback: try keyword-based extraction for unstructured logs
        // Check if the line contains known error patterns
        String upperLine = rawLine.toUpperCase();
        boolean hasError = upperLine.contains("ERROR") || upperLine.contains("EXCEPTION")
                || upperLine.contains("FATAL") || upperLine.contains("FAILED")
                || upperLine.contains("TIMEOUT") || upperLine.contains("REFUSED");
        boolean hasWarn = upperLine.contains("WARN");

        if (hasError || hasWarn) {
            ParsedLogEntry entry = new ParsedLogEntry();
            entry.setTimestamp(LocalDateTime.now());
            entry.setLevel(hasError ? "ERROR" : "WARN");
            entry.setMessage(rawLine);
            entry.setRawLine(rawLine);
            return entry;
        }

        // Still no match — check if it's a stack trace continuation line
        // (indented lines with "at ...", "Caused by:", "Suppressed:", "... N more")
        if (STACK_TRACE_LINE.matcher(rawLine).find()) {
            ParsedLogEntry entry = new ParsedLogEntry();
            entry.setTimestamp(LocalDateTime.now());
            entry.setLevel("ERROR"); // stack trace always follows an error
            entry.setMessage(rawLine);
            entry.setRawLine(rawLine);
            return entry;
        }

        // Still no match — return null so other parsers can try
        return null;
    }

    private ParsedLogEntry buildEntry(String timeStr, String level, String service, String message, String rawLine) {
        ParsedLogEntry entry = new ParsedLogEntry();
        entry.setTimestamp(parseTime(timeStr));
        entry.setLevel(level.toUpperCase());
        entry.setServiceName(service);
        entry.setMessage(message != null ? message.trim() : "");
        entry.setRawLine(rawLine);
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

    @Override
    public String getFormatType() {
        return "PLAIN_TEXT";
    }
}
