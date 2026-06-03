package org.example.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * DeepSeek Chat API request body.
 */
@Data
public class DeepSeekRequest {
    private String model;
    private List<Message> messages;
    private Double temperature;
    @JsonProperty("max_tokens")
    private Integer maxTokens;

    @Data
    public static class Message {
        private String role;
        private String content;

        public static Message system(String content) {
            Message m = new Message();
            m.role = "system";
            m.content = content;
            return m;
        }

        public static Message user(String content) {
            Message m = new Message();
            m.role = "user";
            m.content = content;
            return m;
        }
    }
}
