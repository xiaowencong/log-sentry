package org.example.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.Issue;
import org.example.entity.LogEntry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * DeepSeek AI 提示词构建器。
 * <p>
 * 构建系统提示词和用户消息模板，将 Issue 和日志条目组装为结构化的 AI 分析请求。
 */
@Slf4j
@Component
public class PromptBuilder {

    private static final String SYSTEM_PROMPT =
            "你是一个资深的系统运维专家。请分析以下系统日志错误，给出专业的诊断和处理建议。"
                    + "特别注意：从堆栈信息中提取出报错的具体代码位置（类名.方法名(文件名:行号)），"
                    + "分析为什么会出现这个错误，以及应该如何修复。";
    private static final String USER_TEMPLATE =
            "【错误日志】\n" +
                    "%s\n\n" +
                    "【上下文信息】\n" +
                    "- 服务名称: %s\n" +
                    "- 发生时间: %s\n" +
                    "- 近期同类错误次数: %d\n" +
                    "- 当前风险评分: %d/100\n" +
                    "- 匹配规则分类: %s\n\n" +
                    "【输出要求】\n" +
                    "请严格按以下 JSON 格式返回（不要包含其他内容，不要用 markdown 代码块包裹）：\n" +
                    "{\n" +
                    "  \"summary\": \"问题一句话摘要\",\n" +
                    "  \"rootCause\": \"根因分析 (详细说明为什么会报错)\",\n" +
                    "  \"errorLocation\": \"报错代码位置，格式如 org.example.OrderService.createOrder(OrderService.java:156)，如果是定位错误的日志则填 ERROR日志，不需AI分析\",\n" +
                    "  \"riskLevel\": \"CRITICAL|HIGH|MEDIUM|LOW\",\n" +
                    "  \"suggestions\": [\"建议1\", \"建议2\", \"建议3\"],\n" +
                    "  \"needImmediateAction\": true,\n" +
                    "  \"relatedKnowledge\": \"相关知识或最佳实践\"\n" +
                    "}";
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构建包含错误日志和上下文信息的用户消息。
     */
    public String buildUserMessage(Issue issue, List<LogEntry> logEntries) {
        // 发送原始日志行，让 AI 获得完整堆栈信息、类名、行号
        String logsText = logEntries.stream()
                .map(LogEntry::getRawLine)
                .filter(line -> line != null && !line.isEmpty())
                .limit(50)
                .collect(Collectors.joining("\n"));

        String serviceName = issue.getServiceName() != null ? issue.getServiceName() : "未知";
        String firstSeen = issue.getFirstSeen() != null ? issue.getFirstSeen().toString() : "?";
        String category = issue.getCategory() != null ? issue.getCategory() : "未分类";
        int score = issue.getRiskScore() != null ? issue.getRiskScore() : 0;

        return String.format(USER_TEMPLATE, logsText, serviceName, firstSeen,
                issue.getOccurrenceCount(), score, category);
    }

    /**
     * 解析 AI 响应 JSON 为结构化结果。
     * 自动去除 Markdown 代码块包裹（```json ... ```）。
     */
    public AiAnalysisResult parseResponse(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        // 去除可能的 Markdown 代码块包裹
        String json = content.trim();
        if (json.startsWith("```")) {
            int start = json.indexOf('\n');
            int end = json.lastIndexOf("```");
            if (start > 0 && end > start) {
                json = json.substring(start + 1, end).trim();
            }
        }

        try {
            return objectMapper.readValue(json, AiAnalysisResult.class);
        } catch (Exception e) {
            log.warn("AI 响应 JSON 解析失败: {}", e.getMessage());
            log.debug("原始响应: {}", content);
            return null;
        }
    }

    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }
}
