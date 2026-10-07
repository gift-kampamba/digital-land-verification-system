// DashboardStats.java
package com.landverification.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardStats {
    private Long pendingReviews;
    private Long approvedL1;
    private Long approvedThisMonth;
    private Long totalParcels;
    private Long rejectedThisMonth;
}