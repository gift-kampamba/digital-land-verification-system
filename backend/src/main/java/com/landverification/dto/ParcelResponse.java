package com.landverification.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ParcelResponse {
    private Integer parcelId;
    private String parcelNumber;
    private String plotNumber;
    private String titleDeedNumber;
    private String blockSection;
    private String province;
    private String district;
    private String locationAddress;
    private String gpsLat;
    private String gpsLng;
    private String gpsCoordinates;
    private BigDecimal areaSqm;
    private String landUse;
    private String landCategory;
    private String status;
    private String blockchainHash;
    private String documentHash;
    private String titleDeedUrl;
    private String ownerFullName;
    private String ownerPhone;
    private String ownerNationalId;
    private String ownerAddress;
    private String ownerDistrict;
    private String ownerGender;
    private String ownershipType;
    private String acquisitionMethod;
    private LocalDateTime ownershipStartDate;
    private LocalDateTime ownershipEndDate;
    private String ownerEmail;
    private Integer ownerUserId;
    private String ownerPhotoPath;
    private String ownerSignature;
    private Integer registeredByOfficerId;
    private boolean ownerAccountCreated;
    private boolean ownerEmailSent;
    private LocalDateTime registeredAt;
    private LocalDateTime updatedAt;
    private String registrationStatus;
    private String verificationStatus;
    private List<DocumentResponse> documents;
    private List<String> registrationHistory;
    private String approvalStatus;
    private String approvalStage;
    private List<String> approvalHistory;
}
