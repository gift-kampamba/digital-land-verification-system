package com.landverification.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "land_parcel")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LandParcel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "parcel_id")
    private Integer parcelId;

    @Column(name = "parcel_number", nullable = false, unique = true, length = 50)
    private String parcelNumber;

    @Column(name = "plot_number", length = 50)
    private String plotNumber;

    @Column(name = "title_deed_number", unique = true, length = 50)
    private String titleDeedNumber;

    @Column(name = "block_section", length = 100)
    private String blockSection;

    @Column(nullable = false, length = 100)
    private String province;

    @Column(nullable = false, length = 100)
    private String district;

    @Column(name = "location_address", nullable = false)
    private String locationAddress;

    @Column(name = "gps_lat", length = 50)
    private String gpsLat;

    @Column(name = "gps_lng", length = 50)
    private String gpsLng;

    @Column(name = "area_sqm", nullable = false, precision = 15, scale = 2)
    private BigDecimal areaSqm;

    @Enumerated(EnumType.STRING)
    @Column(name = "land_use", nullable = false)
    private LandUse landUse;

    @Enumerated(EnumType.STRING)
    @Column(name = "land_category")
    private LandCategory landCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ParcelStatus status = ParcelStatus.ACTIVE;

    @Column(name = "blockchain_hash", unique = true)
    private String blockchainHash;

    @Column(name = "document_hash", length = 128)
    private String documentHash;

    @Column(name = "title_deed_file", unique = true)
    private String titleDeedFile;

    @Column(name = "owner_photo_path", length = 255)
    private String ownerPhotoPath;

    @Column(name = "registered_by_officer_id")
    private Integer registeredByOfficerId;

    @Column(name = "registered_at")
    private LocalDateTime registeredAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        registeredAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum LandUse {
        RESIDENTIAL, COMMERCIAL, AGRICULTURAL, INDUSTRIAL, MIXED_USE
    }

    public enum LandCategory {
        URBAN, RURAL, PERI_URBAN, DESIGNATED_AREA
    }

    public enum ParcelStatus {
        ACTIVE, PENDING_TRANSFER, DISPUTED, ARCHIVED
    }
}
