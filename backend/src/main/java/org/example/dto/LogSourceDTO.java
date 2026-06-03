package org.example.dto;

import lombok.Data;

@Data
public class LogSourceDTO {
    private Long id;
    private String name;
    private String sourceType;
    private String path;
    private String formatType;
    private String scanMode;
    private Integer batchSize;
    private Integer batchIntervalMs;
    private String parserConfig;
    private Boolean enabled;
    private String lastCollectTime;
    private String createTime;
}
