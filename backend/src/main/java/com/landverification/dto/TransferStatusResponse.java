package com.landverification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Transfer Status Response DTO
 * Contains detailed status information about a transfer request
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferStatusResponse {
    private Integer requestId;
    private String parcelNumber;
    private String seller;
    private String sellerName;
    private String buyer;
    private String buyerName;
    private Double agreedPriceZmw;
    private Double price;
    private String status;
    private String transferReason;
    private String reason;
    private String supportingDocs;
    private String documents;
    private List<DocumentResponse> supportingDocuments;
    private String submittedAt;
    private String createdAt;

    private String parcelTitleDeedNumber;
    private String parcelProvince;
    private String parcelLocationAddress;
    private String parcelLandUse;
    private Double parcelAreaSqm;
    private String parcelRegisteredAt;
    private String parcelStatus;
    private String documentHash;
    private Boolean ownerAuthenticated;
    private Boolean buyerAuthenticated;
    private String currentOwnerName;
    private String currentOwnerMaskedId;
    private Boolean mismatchDetected;
    private List<String> mismatchReasons;
}
