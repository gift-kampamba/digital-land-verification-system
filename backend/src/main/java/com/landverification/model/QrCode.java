package com.landverification.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "qr_code")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QrCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "qr_id")
    private Integer qrId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parcel_id", nullable = false, unique = true)
    private LandParcel parcel;

    @Column(name = "qr_data", nullable = false, length = 500)
    private String qrData;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "is_valid", nullable = false)
    @Builder.Default
    private Boolean isValid = true;

    @PrePersist
    protected void onCreate() {
        generatedAt = LocalDateTime.now();
    }
}
