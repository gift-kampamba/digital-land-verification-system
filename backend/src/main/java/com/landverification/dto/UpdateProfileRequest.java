package com.landverification.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UpdateProfileRequest {
    private String fullName;
    private String phoneNumber;
    private String address;
    private String gender;
    private MultipartFile photo;
    private String currentPassword;
    private String newPassword;
}