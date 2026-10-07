package com.landverification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterOfficerRequest {
    @NotBlank(message = "Invitation code is required")
    private String invitationCode;

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "National ID is required")
    private String nationalId;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;
}