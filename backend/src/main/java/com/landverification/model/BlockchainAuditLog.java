package com.landverification.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "blockchain_audit_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockchainAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Integer logId;

    @Column(name = "transaction_hash", nullable = false, unique = true)
    private String transactionHash;

    @Column(name = "on_chain")
    private Boolean onChain = true;

    @Column(name = "block_number")
    private Long blockNumber;

    @Column(name = "contract_address")
    private String contractAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Column(name = "related_parcel_id")
    private Integer relatedParcelId;

    @Column(name = "related_request_id")
    private Integer relatedRequestId;

    @Column(name = "initiated_by")
    private Integer initiatedBy;

    @Column(name = "gas_used")
    private Long gasUsed;

    @Column(name = "payload_hash")
    private String payloadHash;

    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        recordedAt = LocalDateTime.now();
    }

    public enum EventType {
        PARCEL_REGISTERED, OWNERSHIP_RECORDED, TRANSFER_INITIATED,
        TRANSFER_APPROVED, TRANSFER_REJECTED, RECORD_VERIFIED
    }
}
