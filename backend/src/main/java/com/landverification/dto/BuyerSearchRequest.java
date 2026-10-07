package com.landverification.dto;

import lombok.Data;

@Data
public class BuyerSearchRequest {
    // At least one of these must be provided for search
    private String nrc;
    private String email;
    private String accountNumber;
}
