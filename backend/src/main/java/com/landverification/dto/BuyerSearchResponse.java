package com.landverification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuyerSearchResponse {
    private Integer userId;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String nationalId;
    private String address;
    private String district;
    private String role;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
