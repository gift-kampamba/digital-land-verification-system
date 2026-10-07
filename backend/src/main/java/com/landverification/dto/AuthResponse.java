package com.landverification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String email;
    private String fullName;
    private String role;
    private Integer userId;
    private Boolean profileComplete;
    private String phoneNumber;
    private String address;
    private String photoPath;
    private String gender;
    private String username;
    private String district;
}