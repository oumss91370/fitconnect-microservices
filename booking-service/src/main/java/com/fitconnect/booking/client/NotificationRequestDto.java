package com.fitconnect.booking.client;

public record NotificationRequestDto(Long userId, String email, String type, String subject, String content) {
}
