package com.landverification.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class VerifyResponse {
    private String parcelNumber;
    private String province;
    private String district;
    private String locationAddress;
    private BigDecimal areaSqm;
    private String landUse;
    private String currentOwnerName;
    private String currentOwnerMaskedId;
    private String blockchainHash;
    private String documentHash;
    private String blockchainVerificationStatus;
    private List<String> ownershipHistory;
    private LocalDateTime registeredAt;
}
