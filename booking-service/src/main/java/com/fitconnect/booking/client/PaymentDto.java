package com.fitconnect.booking.client;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentDto(Long id, String paymentReference, Long bookingId, BigDecimal amount, String paymentMethod,
                         String transactionId, LocalDateTime paymentDate, String status, String failureReason) {

    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }
}
