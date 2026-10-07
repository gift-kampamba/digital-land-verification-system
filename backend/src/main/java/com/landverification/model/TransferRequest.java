package com.landverification.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transfer_request")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Integer requestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parcel_id", nullable = false)
    private LandParcel parcel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_user_id", nullable = false)
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_user_id", nullable = true)
    private User buyer;

    @Column(name = "buyer_full_name")
    private String buyerFullName;

    @Column(name = "buyer_national_id", length = 100)
    private String buyerNationalId;

    @Column(name = "buyer_email", length = 150)
    private String buyerEmail;

    @Column(name = "buyer_phone_number", length = 20)
    private String buyerPhoneNumber;

    @Column(name = "buyer_address", columnDefinition = "TEXT")
    private String buyerAddress;

    @Column(name = "buyer_nationality", length = 50)
    private String buyerNationality;

    @Column(name = "buyer_id_type", length = 50)
    private String buyerIdType;

    @Column(name = "transfer_date")
    private String transferDate;

    @Column(name = "payment_terms")
    private String paymentTerms;

    @Column(name = "transfer_reason")
    private String transferReason;

    @Column(name = "agreed_price_zmw", precision = 15, scale = 2)
    private BigDecimal agreedPriceZmw;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, columnDefinition = "VARCHAR(30)")
    @Builder.Default
    private TransferStatus status = TransferStatus.SUBMITTED;

    @Column(name = "supporting_docs", columnDefinition = "TEXT")
    private String supportingDocs;

    @Column(name = "owner_signature", columnDefinition = "TEXT")
    private String ownerSignature;

    @Column(name = "buyer_signature", columnDefinition = "TEXT")
    private String buyerSignature;

    @Column(name = "seller_photo_path", length = 255)
    private String sellerPhotoPath;

    @Column(name = "buyer_photo_path", length = 255)
    private String buyerPhotoPath;

    @Column(name = "blockchain_tx_hash", unique = true, length = 255)
    private String blockchainTxHash;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean flagged = false;

    @Column(name = "flag_reason", columnDefinition = "TEXT")
    private String flagReason;

    @Column(name = "flagged_at")
    private LocalDateTime flaggedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flagged_by_user_id")
    private User flaggedBy;

    @PrePersist
    protected void onCreate() {
        submittedAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum TransferStatus {
        SUBMITTED, UNDER_REVIEW, APPROVED_L1, APPROVED_L2, APPROVED, FLAGGED, REJECTED, CANCELLED
    }
}
