package com.landverification.dto;

import com.landverification.model.Notification;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NotificationResponse {
    private Integer notificationId;
    private String recipientName;
    private String message;
    private String notificationType;
    private Integer relatedParcelId;
    private Integer relatedRequestId;
    private Boolean isRead;
    private LocalDateTime createdAt;

    public static NotificationResponse fromEntity(Notification notification) {
        NotificationResponse response = new NotificationResponse();
        response.setNotificationId(notification.getNotificationId());
        response.setRecipientName(notification.getRecipient() != null ? notification.getRecipient().getFullName() : null);
        response.setMessage(notification.getMessage());
        response.setNotificationType(notification.getNotificationType() != null ? notification.getNotificationType().name() : null);
        response.setRelatedParcelId(notification.getRelatedParcelId());
        response.setRelatedRequestId(notification.getRelatedRequestId());
        response.setIsRead(notification.getIsRead());
        response.setCreatedAt(notification.getCreatedAt());
        return response;
    }
}
