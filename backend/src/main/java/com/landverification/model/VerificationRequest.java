package com.landverification.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "verification_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerificationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "verification_id")
    private Integer verificationId;

    @Column(name = "parcel_number", nullable = false, length = 50)
    private String parcelNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by")
    private User requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_type", nullable = false)
    @Builder.Default
    private VerificationType verificationType = VerificationType.OWNERSHIP_CHECK;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private VerificationResult result = VerificationResult.PENDING;

    @Column(name = "result_details", columnDefinition = "TEXT")
    private String resultDetails;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @PrePersist
    protected void onCreate() {
        verifiedAt = LocalDateTime.now();
    }

    public enum VerificationType {
        OWNERSHIP_CHECK, HISTORY_CHECK, BLOCKCHAIN_INTEGRITY_CHECK
    }

    public enum VerificationResult {
        VERIFIED, MISMATCH_DETECTED, PARCEL_NOT_FOUND, PENDING
    }
}
