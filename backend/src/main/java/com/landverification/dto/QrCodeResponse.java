package com.landverification.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class QrCodeResponse {
    private Integer qrId;
    private Integer parcelId;
    private String qrData;
    private LocalDateTime generatedAt;
    private Boolean isValid;
}
