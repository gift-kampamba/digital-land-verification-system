package com.landverification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class CompleteProfileRequest {
    @NotBlank(message = "Address is required")
    private String address;

    private MultipartFile photo;
}