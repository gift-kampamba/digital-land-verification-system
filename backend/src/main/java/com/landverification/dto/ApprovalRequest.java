// ApprovalRequest.java
package com.landverification.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

@Data
public class ApprovalRequest {
    @NotNull(message = "Request ID is required")
    private Long requestId;
    
    private String comments;
    
    private String digitalSignature;
}