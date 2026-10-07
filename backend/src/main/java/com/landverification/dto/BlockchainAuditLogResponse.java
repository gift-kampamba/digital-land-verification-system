package com.landverification.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BlockchainAuditLogResponse {
    private Integer logId;
    private String transactionHash;
    private Long blockNumber;
    private String contractAddress;
    private String eventType;
    private Integer relatedParcelId;
    private Integer relatedRequestId;
    private Integer initiatedBy;
    private Long gasUsed;
    private String payloadHash;
    private LocalDateTime recordedAt;
}
