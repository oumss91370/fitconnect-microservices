package com.fitconnect.notification.service;

import com.fitconnect.notification.dto.NotificationRequest;
import com.fitconnect.notification.dto.NotificationResponse;
import com.fitconnect.notification.exception.NotificationConflictException;
import com.fitconnect.notification.exception.ResourceNotFoundException;
import com.fitconnect.notification.model.Notification;
import com.fitconnect.notification.model.NotificationStatus;
import com.fitconnect.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final NotificationSender sender;

    public NotificationService(NotificationRepository repository, NotificationSender sender) {
        this.repository = repository;
        this.sender = sender;
    }

    /** Enregistre la notification (PENDING) puis tente l'envoi -> SENT ou FAILED. */
    @Transactional
    public NotificationResponse send(NotificationRequest r) {
        Notification n = Notification.builder()
                .userId(r.userId())
                .email(r.email())
                .type(r.type())
                .subject(r.subject())
                .content(r.content())
                .status(NotificationStatus.PENDING)
                .build();
        n = repository.save(n);
        attempt(n);
        return NotificationResponse.from(n);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> findByUser(Long userId) {
        return repository.findByUserIdOrderByIdDesc(userId).stream().map(NotificationResponse::from).toList();
    }

    /** Notifications à (ré)envoyer : PENDING, et FAILED si includeFailed. */
    @Transactional(readOnly = true)
    public List<NotificationResponse> findPending(boolean includeFailed) {
        Set<NotificationStatus> statuses = includeFailed
                ? Set.of(NotificationStatus.PENDING, NotificationStatus.FAILED)
                : Set.of(NotificationStatus.PENDING);
        return repository.findByStatusInOrderByIdAsc(statuses).stream().map(NotificationResponse::from).toList();
    }

    @Transactional
    public NotificationResponse retry(Long id) {
        Notification n = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification " + id + " introuvable"));
        if (n.getStatus() == NotificationStatus.SENT) {
            throw new NotificationConflictException("La notification " + id + " a déjà été envoyée");
        }
        attempt(n);
        return NotificationResponse.from(n);
    }

    private void attempt(Notification n) {
        n.setAttempts(n.getAttempts() + 1);
        n.setSentDate(LocalDateTime.now());
        try {
            sender.send(n.getEmail(), n.getSubject(), n.getContent());
            n.setStatus(NotificationStatus.SENT);
            n.setLastError(null);
        } catch (RuntimeException e) {
            log.warn("Échec d'envoi de la notification {} : {}", n.getId(), e.getMessage());
            n.setStatus(NotificationStatus.FAILED);
            n.setLastError(e.getMessage());
        }
    }
}
