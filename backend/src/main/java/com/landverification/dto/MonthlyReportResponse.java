package com.landverification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyReportResponse {
    private String reportPeriod;
    private String periodLabel;
    private String generatedAt;
    private String reportId;
    private String dateRange;
    private List<String> trendMonths;
    private TrendData trendData;
    private DistrictData districtData;
    private LandUseData landUseData;
    private List<WeeklyTransfer> transfersByWeek;
    private List<MetricRow> registrationRows;
    private List<MetricRow> transferRows;
    private List<MetricRow> approvalRows;
    private List<OfficerPerformanceRow> officerRows;
    private List<MetricRow> blockchainRows;
    private List<MetricRow> fraudRows;
    private List<ActivityLogRow> activityLog;
    private String executiveSummary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendData {
        private List<Long> registrations;
        private List<Long> transfers;
        private List<Long> approved;
        private List<Long> flagged;
        private List<Long> rejected;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DistrictData {
        private List<String> labels;
        private List<Long> values;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LandUseData {
        private List<String> labels;
        private List<Long> values;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeeklyTransfer {
        private String wk;
        private String dates;
        private Long val;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricRow {
        private String label;
        private String value;
        private String delta;
        private boolean positive;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OfficerPerformanceRow {
        private String officer;
        private Long registrations;
        private Long transfers;
        private Long pending;
        private Integer performance;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityLogRow {
        private String reference;
        private String action;
        private String type;
        private String district;
        private String officer;
        private String date;
        private LocalDateTime activityAt;
        private String status;
        private boolean blockchain;
    }
}
