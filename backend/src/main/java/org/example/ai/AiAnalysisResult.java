package org.example.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * AI 分析结果 —— 从 DeepSeek 响应中解析出的结构化分析结果。
 */
@Data
public class AiAnalysisResult {
    private String summary;
    private String rootCause;
    private String riskLevel;
    private List<String> suggestions;
    private String errorLocation;

    @JsonProperty("needImmediateAction")
    private Boolean needImmediateAction;

    private String relatedKnowledge;
}
