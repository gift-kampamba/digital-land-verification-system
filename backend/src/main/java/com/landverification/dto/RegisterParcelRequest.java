package com.landverification.dto;

import com.landverification.model.LandOwnership;
import com.landverification.model.LandParcel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RegisterParcelRequest {
    @NotBlank(message = "Parcel number is required")
    private String parcelNumber;

    private String titleDeedNumber;

    @NotBlank(message = "Province is required")
    private String province;

    @NotBlank(message = "District is required")
    private String district;

    @NotBlank(message = "Location address is required")
    private String locationAddress;

    @NotNull(message = "Area is required")
    @Positive(message = "Area must be positive")
    private BigDecimal areaSqm;

    @NotNull(message = "Land use is required")
    private LandParcel.LandUse landUse;

    @NotNull(message = "Owner user ID is required")
    private Integer ownerUserId;

    @NotBlank(message = "Owner wallet address is required")
    private String ownerWalletAddress;

    @NotNull(message = "Ownership type is required")
    private LandOwnership.OwnershipType ownershipType;

    @NotNull(message = "Acquisition method is required")
    private LandOwnership.AcquisitionMethod acquisitionMethod;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotBlank(message = "Document hash is required")
    private String documentHash;
}
