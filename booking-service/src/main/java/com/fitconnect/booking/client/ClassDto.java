package com.fitconnect.booking.client;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Vue du cours renvoyée par class-service (champs utiles au booking-service). */
public record ClassDto(Long id, String name, String instructor, String gymLocation, BigDecimal price,
                       LocalDateTime dateTime, String status, Integer maxParticipants,
                       Integer currentParticipants, Integer availableSpots) {
}
