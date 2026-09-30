package com.fitconnect.notification.dto;

import com.fitconnect.notification.model.Notification;
import com.fitconnect.notification.model.NotificationStatus;
import com.fitconnect.notification.model.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(Long id, Long userId, String email, NotificationType type, String subject,
                                   String content, LocalDateTime sentDate, NotificationStatus status,
                                   int attempts, String lastError) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getUserId(), n.getEmail(), n.getType(), n.getSubject(),
                n.getContent(), n.getSentDate(), n.getStatus(), n.getAttempts(), n.getLastError());
    }
}
