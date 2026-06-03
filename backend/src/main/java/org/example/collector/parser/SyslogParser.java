package org.example.collector.parser;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Syslog parser supporting RFC 5424 and RFC 3164 formats.
 * RFC 5424: <PRI>VERSION TIMESTAMP HOSTNAME APP-NAME PROCID MSGID STRUCTURED-DATA MSG
 * RFC 3164: <PRI>TIMESTAMP HOSTNAME MSG
 */
@Component
public class SyslogParser implements LogParser {

    // RFC 5424: <pri>version timestamp hostname app-name procid msgid [structured-data] message
    private static final Pattern RFC5424 = Pattern.compile(
            "^<(\\d+)>" +
                    "(\\d+)\\s+" +
                    "(\\S+)\\s+" +
                    "(\\S+)\\s+" +
                    "(\\S+)\\s+" +
                    "(\\S+)\\s+" +
                    "(\\S+)\\s+" +
                    "(?:\\[([^\\]]*)\\])?\\s*" +
                    "(.*)$"
    );

    // RFC 3164: <pri>timestamp hostname message
    private static final Pattern RFC3164 = Pattern.compile(
            "^<(\\d+)>" +
                    "([A-Z][a-z]{2}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2})\\s+" +
                    "(\\S+)\\s+" +
                    "(.*)$"
    );

    private static final DateTimeFormatter[] SYSLOG_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS][XXX][Z]"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
    };

    @Override
    public ParsedLogEntry parse(String rawLine) {
        if (rawLine == null || rawLine.trim().isEmpty()) {
            return null;
        }

        // Try RFC 5424
        Matcher m5424 = RFC5424.matcher(rawLine);
        if (m5424.matches()) {
            return parseRfc5424(m5424, rawLine);
        }

        // Try RFC 3164
        Matcher m3164 = RFC3164.matcher(rawLine);
        if (m3164.matches()) {
            return parseRfc3164(m3164, rawLine);
        }

        return null;
    }

    private ParsedLogEntry parseRfc5424(Matcher m, String rawLine) {
        int pri = Integer.parseInt(m.group(1));
        String version = m.group(2);
        String timestamp = m.group(3);
        String hostname = m.group(4);
        String appName = m.group(5);
        String procId = m.group(6);
        String msgId = m.group(7);
        String structuredData = m.group(8);
        String message = m.group(9);

        ParsedLogEntry entry = new ParsedLogEntry();
        entry.setRawLine(rawLine);
        entry.setTimestamp(parseTimestamp(timestamp));
        entry.setLevel(mapPriorityToLevel(pri));
        entry.setServiceName(appName != null ? appName : hostname);
        entry.setMessage(message != null ? message.trim() : "");

        if (hostname != null) entry.getMetadata().put("hostname", hostname);
        if (structuredData != null) entry.getMetadata().put("structuredData", structuredData);
        entry.getMetadata().put("syslogVersion", version);

        return entry;
    }

    private ParsedLogEntry parseRfc3164(Matcher m, String rawLine) {
        int pri = Integer.parseInt(m.group(1));
        String timestamp = m.group(2);
        String hostname = m.group(3);
        String message = m.group(4);

        ParsedLogEntry entry = new ParsedLogEntry();
        entry.setRawLine(rawLine);
        entry.setTimestamp(parseRfc3164Timestamp(timestamp));
        entry.setLevel(mapPriorityToLevel(pri));
        entry.setServiceName(hostname);
        entry.setMessage(message != null ? message.trim() : "");
        entry.getMetadata().put("hostname", hostname);

        return entry;
    }

    private LocalDateTime parseTimestamp(String ts) {
        if (ts == null) return LocalDateTime.now();
        for (DateTimeFormatter fmt : SYSLOG_FORMATTERS) {
            try {
                return LocalDateTime.parse(ts, fmt);
            } catch (Exception ignored) {
            }
        }
        // Try ZonedDateTime
        try {
            return ZonedDateTime.parse(ts).toLocalDateTime();
        } catch (Exception ignored) {
        }
        return LocalDateTime.now();
    }

    private LocalDateTime parseRfc3164Timestamp(String ts) {
        // Format: "Jun  2 10:30:00" or "Jun 02 10:30:00"
        if (ts == null) return LocalDateTime.now();
        int currentYear = LocalDateTime.now().getYear();
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM  d HH:mm:ss").withLocale(java.util.Locale.ENGLISH);
            return LocalDateTime.parse(currentYear + " " + ts, DateTimeFormatter.ofPattern("yyyy MMM  d HH:mm:ss").withLocale(java.util.Locale.ENGLISH));
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private String mapPriorityToLevel(int priority) {
        // Syslog severity: 0=Emergency, 1=Alert, 2=Critical, 3=Error, 4=Warning, 5=Notice, 6=Info, 7=Debug
        int severity = priority & 0x07;
        switch (severity) {
            case 0:
                return "EMERGENCY";
            case 1:
                return "ALERT";
            case 2:
                return "CRITICAL";
            case 3:
                return "ERROR";
            case 4:
                return "WARN";
            case 5:
                return "NOTICE";
            case 6:
                return "INFO";
            case 7:
                return "DEBUG";
            default:
                return "UNKNOWN";
        }
    }

    @Override
    public String getFormatType() {
        return "SYSLOG";
    }
}
