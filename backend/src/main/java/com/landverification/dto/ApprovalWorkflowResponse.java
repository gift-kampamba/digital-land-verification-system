package com.landverification.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ApprovalWorkflowResponse {
    private Integer approvalId;
    private Integer requestId;
    private String officerName;
    private Integer approvalLevel;
    private String action;
    private String comments;
    private String digitalSignature;
    private LocalDateTime actionedAt;
    private Integer staffId;
}
