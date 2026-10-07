package com.landverification.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "land_ownership")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LandOwnership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ownership_id")
    private Integer ownershipId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parcel_id", nullable = false)
    private LandParcel parcel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "ownership_type", nullable = false)
    @Builder.Default
    private OwnershipType ownershipType = OwnershipType.SOLE;

    @Enumerated(EnumType.STRING)
    @Column(name = "acquisition_method", nullable = false)
    private AcquisitionMethod acquisitionMethod;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "is_current", nullable = false)
    @Builder.Default
    private Boolean isCurrent = true;

    @Column(name = "blockchain_hash", unique = true)
    private String blockchainHash;

    @Column(name = "owner_signature", columnDefinition = "TEXT")
    private String ownerSignature;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum OwnershipType {
        SOLE, JOINT, LEASEHOLD, FREEHOLD
    }

    public enum AcquisitionMethod {
        ORIGINAL_REGISTRATION, PURCHASE, INHERITANCE, GIFT, COURT_ORDER
    }
}
