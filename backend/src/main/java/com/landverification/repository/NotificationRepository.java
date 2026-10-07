package com.landverification.repository;

import com.landverification.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Integer> {
    List<Notification> findByRecipient_UserIdOrderByCreatedAtDesc(Integer userId);
    List<Notification> findByRecipient_UserIdAndIsReadFalse(Integer userId);
    long countByRecipient_UserIdAndIsReadFalse(Integer userId);
    boolean existsByRelatedRequestIdAndNotificationType(Integer relatedRequestId, Notification.NotificationType notificationType);
}
