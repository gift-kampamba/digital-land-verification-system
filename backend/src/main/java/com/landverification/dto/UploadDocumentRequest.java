package com.landverification.dto;

import com.landverification.model.Document;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UploadDocumentRequest {
    @NotNull(message = "Related type is required")
    private Document.RelatedType relatedType;

    @NotNull(message = "Related ID is required")
    private Integer relatedId;

    @NotNull(message = "Document type is required")
    private Document.DocumentType documentType;

    @NotBlank(message = "File name is required")
    private String fileName;

    @NotBlank(message = "File path is required")
    private String filePath;

    private String fileHash;
}
