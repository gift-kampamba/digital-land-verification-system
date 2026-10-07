package com.landverification.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id")
    private Integer documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "related_type", nullable = false)
    private RelatedType relatedType;

    @Column(name = "related_id", nullable = false)
    private Integer relatedId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private DocumentType documentType;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_hash")
    private String fileHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }

    public enum RelatedType { PARCEL, TRANSFER_REQUEST }

    public enum DocumentType {
        TITLE_DEED, SURVEY_PLAN, ID_COPY,
        PROOF_OF_PAYMENT, CONSENT_FORM, COURT_ORDER, OTHER
    }
}
