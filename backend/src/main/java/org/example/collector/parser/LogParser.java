package org.example.collector.parser;

public interface LogParser {

    /**
     * Parse a raw log line into structured fields.
     *
     * @param rawLine the raw log line
     * @return ParsedLogEntry with extracted fields, or null if cannot parse
     */
    ParsedLogEntry parse(String rawLine);

    /**
     * @return the format type this parser handles (e.g. "PLAIN_TEXT", "JSON", "SYSLOG", "LOGBACK")
     */
    String getFormatType();
}
