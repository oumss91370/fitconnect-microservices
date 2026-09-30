package com.fitconnect.booking;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Paramètres métier (config-repo/booking-service.yml, préfixe fitconnect.booking). */
@ConfigurationProperties(prefix = "fitconnect.booking")
public record BookingProperties(Duration paymentTimeout, Duration cancellationNotice, Duration reminderBefore,
                                boolean demoEndpointsEnabled) {

    public BookingProperties {
        if (paymentTimeout == null) paymentTimeout = Duration.ofHours(1);
        if (cancellationNotice == null) cancellationNotice = Duration.ofHours(24);
        if (reminderBefore == null) reminderBefore = Duration.ofHours(24);
    }
}
