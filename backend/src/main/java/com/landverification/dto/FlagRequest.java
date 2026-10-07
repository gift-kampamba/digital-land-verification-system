package com.landverification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FlagRequest {
    @NotNull(message = "Request ID is required")
    private Integer requestId;

    @NotBlank(message = "Flag reason is required")
    private String reason;
}
