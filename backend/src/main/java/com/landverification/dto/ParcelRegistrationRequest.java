package com.landverification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ParcelRegistrationRequest {
    // Plot Details
    private String parcelNumber;
    private String plotNumber;
    private String blockSection;
    @NotNull(message = "Plot size is required")
    @Positive(message = "Plot size must be positive")
    private Integer plotSize;
    @NotBlank(message = "Land use type is required")
    private String landUseType;
    private String landCategory;
    private String tenureType;
    private String leaseDuration;
    @NotBlank(message = "District is required")
    private String district;
    @NotBlank(message = "Province is required")
    private String province;
    @NotBlank(message = "Location address is required")
    private String locationAddress;
    private String gpsLat;
    private String gpsLng;
    private String plotValue;
    private String encumbrances;
    private String encumbranceDetail;
    
    // Owner Information
    @NotBlank(message = "Ownership type is required")
    private String ownershipType;
    private String ownerName;
    @jakarta.validation.constraints.Pattern(regexp = "^[A-Za-z0-9/\\-]{8,20}$", message = "Owner national ID must be 8-20 characters and contain only letters, numbers, / or -")
    private String ownerNid;
    @jakarta.validation.constraints.Pattern(regexp = "^\\+260[0-9]{9}$", message = "Owner phone number must start with +260 and include 9 digits after the code")
    private String ownerPhone;
    @jakarta.validation.constraints.Email(message = "Invalid owner email format")
    private String ownerEmail;
    private String ownerGender;
    private String ownerAddress;
    private String ownerOccupation;
    private String maritalStatus;
    private String nokName;
    private String nokPhone;
    private String nokRelation;
    private Integer ownerUserId;
    private String ownerSignature;
    
    // Joint Owner
    private String owner2Name;
    private String owner2Nid;
    
    // Company Details
    private String companyName;
    private String pacraNumber;
    private String directorName;
    private String directorNid;
    
    // Optional blockchain / owner wallet information
    private String ownerWalletAddress;

    private String documentHash;

    // Payment Information
    private String paymentMethod;
    private String paymentRef;
    private String paymentDate;
}