package com.landverification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Approval Audit Response DTO
 * Contains the complete approval history/audit trail for a transfer request
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalAuditResponse {
    private Integer requestId;
    private List<ApprovalRecord> approvals;
    private Integer totalApprovals;
    private String currentStatus;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApprovalRecord {
        private String action;
        private String status;
        private String officer;
        private String officerName;
        private Integer approvalLevel;
        private Integer staffId;
        private String approvedAt;
        private String actionedAt;
        private String timestamp;
        private String comments;
        private String digitalSignature;
    }
}
