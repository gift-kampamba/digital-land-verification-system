package com.landverification.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "approval_workflow")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalWorkflow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "approval_id")
    private Integer approvalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private TransferRequest request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "officer_user_id", nullable = false)
    private User officer;

    @Column(name = "approval_level", nullable = false)
    private Integer approvalLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalAction action;

    @Column(columnDefinition = "TEXT")
    private String comments;

    @Column(name = "digital_signature", length = 512)
    private String digitalSignature;

    @Column(name = "actioned_at", nullable = false, 
        columnDefinition = "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime actionedAt;

    @PrePersist
    protected void onCreate() {
        actionedAt = LocalDateTime.now();
    }

    public enum ApprovalAction {
        APPROVED, REJECTED, RETURNED_FOR_CORRECTION
    }
}
