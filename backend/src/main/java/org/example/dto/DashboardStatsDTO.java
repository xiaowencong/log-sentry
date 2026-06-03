package org.example.dto;

import lombok.Data;

@Data
public class DashboardStatsDTO {
    private long critical;
    private long high;
    private long medium;
    private long low;
    private long total;
}
