package com.landverification.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Integer notificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipient;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private NotificationType notificationType;

    @Column(name = "related_parcel_id")
    private Integer relatedParcelId;

    @Column(name = "related_request_id")
    private Integer relatedRequestId;

    @Column(name = "related_user_id")
    private Integer relatedUserId;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum NotificationType {
        TRANSFER_INITIATED, TRANSFER_APPROVED_L1, TRANSFER_APPROVED_L2,
        TRANSFER_APPROVED_FINAL, TRANSFER_REJECTED, TRANSFER_FLAGGED,
        TRANSFER_MISMATCH_DETECTED, REGISTRATION_COMPLETE, VERIFICATION_COMPLETE,
        PROFILE_CHANGE_REQUESTED, PROFILE_CHANGE_APPROVED, PROFILE_CHANGE_REJECTED
    }
}
