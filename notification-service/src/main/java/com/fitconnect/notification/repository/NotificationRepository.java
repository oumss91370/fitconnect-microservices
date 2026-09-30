package com.fitconnect.notification.repository;

import com.fitconnect.notification.model.Notification;
import com.fitconnect.notification.model.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByIdDesc(Long userId);

    List<Notification> findByStatusInOrderByIdAsc(Collection<NotificationStatus> statuses);
}
