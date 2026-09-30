package com.fitconnect.booking.client;

public record NotificationDto(Long id, Long userId, String email, String type, String subject, String status) {
}
