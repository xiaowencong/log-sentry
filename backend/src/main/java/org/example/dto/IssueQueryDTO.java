package org.example.dto;

import lombok.Data;

@Data
public class IssueQueryDTO {
    private String riskLevel;
    private String status;
    private String serviceName;
    private String keyword;
    private String startTime;
    private String endTime;
    private Integer page = 1;
    private Integer size = 20;
}
