// UserResponse.java
package com.landverification.dto;

import com.landverification.model.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {
    private Integer userId;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String address;
    private String gender;
    private String nationalId;
    private String photoPath;
    private String district;
    private String role;
    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;
    private LocalDateTime passwordChangedAt;
    
    public static UserResponse fromEntity(User user) {
        return UserResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .address(user.getAddress())
                .gender(user.getGender() != null ? user.getGender().toString() : null)
                .nationalId(user.getNationalId())
                .photoPath(user.getPhotoPath())
                .district(user.getDistrict())
                .role(user.getRole().toString())
                .createdAt(user.getCreatedAt())
                .lastLogin(user.getLastLoginAt())
                .passwordChangedAt(user.getPasswordChangedAt())
                .build();
    }
}