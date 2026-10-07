package com.landverification.dto;

import com.landverification.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    // Optional — user fills these in from their own dashboard after first login
    private String fullName;
    private String nationalId;
    private String phoneNumber;

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    private User.Role role = User.Role.LAND_OWNER;
    private String district;
}