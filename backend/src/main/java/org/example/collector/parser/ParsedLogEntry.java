package org.example.collector.parser;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
public class ParsedLogEntry {
    private LocalDateTime timestamp;
    private String level;
    private String serviceName;
    private String message;
    private String rawLine;
    private Map<String, Object> metadata = new HashMap<>();
}
