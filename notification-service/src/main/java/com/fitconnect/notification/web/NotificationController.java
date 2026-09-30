package com.fitconnect.notification.web;

import com.fitconnect.notification.dto.NotificationRequest;
import com.fitconnect.notification.dto.NotificationResponse;
import com.fitconnect.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /** Appelé par les autres services : 201 Created (status SENT ou FAILED dans le corps). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationResponse send(@Valid @RequestBody NotificationRequest request) {
        return service.send(request);
    }

    @GetMapping("/user/{userId}")
    public List<NotificationResponse> byUser(@PathVariable Long userId) {
        return service.findByUser(userId);
    }

    /** Notifications en attente d'envoi (PENDING + FAILED par défaut), pour un scheduler de relance. */
    @GetMapping("/pending")
    public List<NotificationResponse> pending(@RequestParam(defaultValue = "true") boolean includeFailed) {
        return service.findPending(includeFailed);
    }

    @PatchMapping("/{id}/retry")
    public NotificationResponse retry(@PathVariable Long id) {
        return service.retry(id);
    }
}
