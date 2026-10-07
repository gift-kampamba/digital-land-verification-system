// TransferResponse.java - Add these missing fields
package com.landverification.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TransferResponse {
    private Integer requestId;
    private String parcelNumber;
    private String sellerName;
    private String sellerNid;      // New field
    private String buyerName;
    private String buyerNid;       // New field
    private String buyerEmail;
    private String buyerPhone;
    private String status;
    private String transferReason;
    private BigDecimal agreedPriceZmw;
    private String blockchainTxHash;
    private LocalDateTime submittedAt;
    private LocalDateTime updatedAt;
    private Boolean flagged;
    private String flagReason;
    private LocalDateTime flaggedAt;
    private String emailStatusMessage;
    
    // Parcel details fields
    private String district;       // New field
    private String landUse;        // New field
    private BigDecimal areaSqm;    // New field
    private String supportingDocs;
    private List<DocumentResponse> supportingDocuments;
    private Boolean hasOwnerSignature;
    private Boolean hasBuyerSignature;
    private String sellerPhotoPath;
    private String buyerPhotoPath;
    private List<ApprovalWorkflowResponse> approvalHistory;
}