package com.fitconnect.booking.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Les notifications sont "best effort" : si notification-service est en panne,
 * la réservation n'est pas bloquée. On journalise et on renvoie null.
 */
@Component
public class NotificationClientFallbackFactory implements FallbackFactory<NotificationClient> {

    private static final Logger log = LoggerFactory.getLogger(NotificationClientFallbackFactory.class);

    @Override
    public NotificationClient create(Throwable cause) {
        return request -> {
            log.warn("Notification {} non envoyée à {} ({}) — la saga continue",
                    request.type(), request.email(), RemoteErrors.technicalReason(cause));
            return null;
        };
    }
}
