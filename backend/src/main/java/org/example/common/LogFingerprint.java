package org.example.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/**
 * 日志指纹工具类 —— 将日志行归一化后计算 MD5 指纹。
 * <p>
 * 去除时间戳、数字、十六进制地址等易变部分，
 * 使得"相同错误模式"产生相同的指纹，用于 Issue 去重聚合。
 */
public class LogFingerprint {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    /**
     * 时间戳去除模式（如 2026-06-02 10:30:00.123）
     */
    private static final Pattern TIMESTAMP = Pattern.compile(
            "\\d{2,4}[-/]\\d{1,2}[-/]\\d{1,2}[T ]\\d{1,2}:\\d{2}:\\d{2}(?:\\.\\d{1,6})?");
    /**
     * 数字序列替换模式（行号、线程ID、PID 等）
     */
    private static final Pattern DIGITS = Pattern.compile("\\b\\d+\\b");
    /**
     * 十六进制地址替换模式（内存地址、对象哈希等）
     */
    private static final Pattern HEX_ADDR = Pattern.compile("\\b0x[a-fA-F0-9]+\\b|[a-fA-F0-9]{8,}");

    public static String compute(String rawLine) {
        if (rawLine == null || rawLine.isEmpty()) {
            return "";
        }
        // 归一化：去除易变部分后再计算哈希
        String normalized = normalize(rawLine);
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }

    /**
     * 归一化日志行：去除时间戳、数字、十六进制地址，合并多个空格。
     * 使得相同错误模式的日志行得到相同的指纹。
     */
    static String normalize(String line) {
        if (line == null || line.isEmpty()) return "";
        // 1. 去除时间戳
        line = TIMESTAMP.matcher(line).replaceAll("");
        // 2. 将十六进制地址和长十六进制字符串替换为占位符
        line = HEX_ADDR.matcher(line).replaceAll("@HEX");
        // 3. 将独立数字序列替换为占位符
        line = DIGITS.matcher(line).replaceAll("#");
        // 4. 合并多个空格
        line = line.replaceAll("\\s+", " ").trim();
        return line;
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hex[i * 2] = HEX_CHARS[v >>> 4];
            hex[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hex);
    }
}
