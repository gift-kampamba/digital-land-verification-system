package com.landverification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.landverification.model.User;

@Data
public class CreateOfficerRequest {
    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Username is required")
    private String username;

    @NotNull(message = "Role is required")
    private User.Role role;

    @NotBlank(message = "District is required")
    private String district;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    private String province;

    @NotBlank(message = "Temporary password is required")
    private String temporaryPassword;
}