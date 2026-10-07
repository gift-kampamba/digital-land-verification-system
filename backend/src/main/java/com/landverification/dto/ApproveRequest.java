package com.landverification.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

@Data
public class ApproveRequest {
    @NotNull(message = "Request ID is required")
    private Long requestId;
    
    private String comments;
    
    private String digitalSignature;

    private String signerAddress;
}
