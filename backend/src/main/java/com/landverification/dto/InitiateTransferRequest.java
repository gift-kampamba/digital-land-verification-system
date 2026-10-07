package com.landverification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class InitiateTransferRequest {
    @NotNull(message = "Parcel ID is required")
    private Integer parcelId;

    // Account verification
    @NotNull(message = "Buyer account status must be specified")
    private Boolean buyerHasExistingAccount; // true if existing, false if new
    
    private Integer existingBuyerUserId; // Set if buyer has existing account
    
    @NotBlank(message = "Buyer national ID is required")
    private String buyerNid;

    @NotBlank(message = "Buyer full name is required")
    private String buyerName;

    @NotBlank(message = "Buyer email is required")
    private String buyerEmail;

    private String buyerPhone;
    private String buyerAddress;
    private String buyerNationality;
    private String buyerIdType;
    private String transferDate;
    private String paymentTerms;
    private String transferReason;
    private BigDecimal agreedPriceZmw;

    @NotBlank(message = "Owner signature is required")
    private String ownerSignature;

    @NotBlank(message = "Buyer signature is required")
    private String buyerSignature;

    private String supportingDocs;
}
