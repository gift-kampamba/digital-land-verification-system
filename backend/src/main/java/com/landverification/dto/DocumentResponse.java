package com.landverification.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DocumentResponse {
    private Long id;
    private String fileName;
    private String filePath;
    private String documentType;
    private String parcelNumber;
    private String category;
    private String notes;
    private String uploaderName;
    private LocalDateTime uploadedAt;
    private String fileSize;
    
}
