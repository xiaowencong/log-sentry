package org.example.collector.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;

/**
 * JSON log parser.
 * Tries common JSON log field names: timestamp/time/@timestamp, level/severity, message/msg,
 * service/serviceName/logger/application.
 */
@Component
public class JsonLogParser implements LogParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS][XXX][Z]");

    // Common field name mappings
    private static final String[] TIME_FIELDS = {"timestamp", "@timestamp", "time", "datetime", "date"};
    private static final String[] LEVEL_FIELDS = {"level", "severity", "log_level", "logLevel"};
    private static final String[] MSG_FIELDS = {"message", "msg", "body", "content", "text"};
    private static final String[] SERVICE_FIELDS = {"service", "serviceName", "service_name", "application", "app", "logger", "logger_name"};

    @Override
    public ParsedLogEntry parse(String rawLine) {
        if (rawLine == null || rawLine.trim().isEmpty()) {
            return null;
        }

        try {
            JsonNode root = MAPPER.readTree(rawLine);
            ParsedLogEntry entry = new ParsedLogEntry();
            entry.setRawLine(rawLine);

            entry.setTimestamp(extractTimestamp(root));
            entry.setLevel(extractFirst(root, LEVEL_FIELDS, "UNKNOWN"));
            entry.setMessage(extractFirst(root, MSG_FIELDS, rawLine));
            entry.setServiceName(extractFirst(root, SERVICE_FIELDS, null));

            // Store remaining fields as metadata
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey();
                boolean isCore = isCoreField(key);
                if (!isCore) {
                    JsonNode value = field.getValue();
                    if (value.isTextual()) {
                        entry.getMetadata().put(key, value.asText());
                    } else if (value.isNumber()) {
                        entry.getMetadata().put(key, value.numberValue());
                    } else {
                        entry.getMetadata().put(key, value.toString());
                    }
                }
            }

            return entry;
        } catch (Exception e) {
            return null; // Not valid JSON, let other parsers try
        }
    }

    private LocalDateTime extractTimestamp(JsonNode root) {
        String timeStr = extractFirst(root, TIME_FIELDS, null);
        if (timeStr == null) return LocalDateTime.now();

        try {
            return LocalDateTime.parse(timeStr, ISO_FORMATTER);
        } catch (Exception ignored) {
        }

        // Try common date-only formats
        try {
            return LocalDateTime.parse(timeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(timeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
        } catch (Exception ignored) {
        }

        // Try as epoch millis
        try {
            long epoch = Long.parseLong(timeStr);
            return LocalDateTime.ofEpochSecond(epoch / 1000, (int) ((epoch % 1000) * 1_000_000), java.time.ZoneOffset.UTC);
        } catch (Exception ignored) {
        }

        return LocalDateTime.now();
    }

    private String extractFirst(JsonNode root, String[] candidates, String defaultVal) {
        for (String field : candidates) {
            JsonNode node = root.get(field);
            if (node != null && !node.isNull()) {
                return node.asText();
            }
        }
        return defaultVal;
    }

    private boolean isCoreField(String key) {
        for (String f : TIME_FIELDS) if (f.equals(key)) return true;
        for (String f : LEVEL_FIELDS) if (f.equals(key)) return true;
        for (String f : MSG_FIELDS) if (f.equals(key)) return true;
        for (String f : SERVICE_FIELDS) if (f.equals(key)) return true;
        return false;
    }

    @Override
    public String getFormatType() {
        return "JSON";
    }
}
