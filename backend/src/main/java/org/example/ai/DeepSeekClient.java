package org.example.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.example.config.DeepSeekConfig;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * DeepSeek Chat API HTTP 客户端。
 * <p>
 * 封装 OkHttp 调用，发送 chat completion 请求并解析响应。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeepSeekClient {

    private final DeepSeekConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    /**
     * 发送 chat completion 请求到 DeepSeek API。
     *
     * @param systemPrompt 系统角色提示词
     * @param userMessage  用户角色消息
     * @return 解析后的响应，失败时返回 null
     */
    public DeepSeekResponse chat(String systemPrompt, String userMessage) {
        DeepSeekRequest request = new DeepSeekRequest();
        request.setModel(config.getModel());
        request.setTemperature(config.getTemperature());
        request.setMaxTokens(config.getMaxTokens());
        request.setMessages(Arrays.asList(
                DeepSeekRequest.Message.system(systemPrompt),
                DeepSeekRequest.Message.user(userMessage)
        ));

        try {
            String requestJson = objectMapper.writeValueAsString(request);

            RequestBody body = RequestBody.create(
                    requestJson,
                    MediaType.parse("application/json; charset=utf-8"));

            Request httpRequest = new Request.Builder()
                    .url(config.getUrl())
                    .addHeader("Authorization", "Bearer " + config.getKey())
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            long startMs = System.currentTimeMillis();
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                long costMs = System.currentTimeMillis() - startMs;

                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    log.error("DeepSeek API 错误: 状态码={}, 响应体={}", response.code(), errorBody);
                    return null;
                }

                String responseJson = response.body() != null ? response.body().string() : "";
                DeepSeekResponse dsResponse = objectMapper.readValue(responseJson, DeepSeekResponse.class);
                log.info("DeepSeek API 调用成功: 模型={}, token数={}, 耗时={}ms",
                        dsResponse.getModel(),
                        dsResponse.getUsage() != null ? dsResponse.getUsage().getTotalTokens() : "?",
                        costMs);
                return dsResponse;
            }
        } catch (IOException e) {
            log.error("DeepSeek API 调用失败: {}", e.getMessage());
            return null;
        }
    }
}
