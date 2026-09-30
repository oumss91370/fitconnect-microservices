package com.fitconnect.booking.client;

import java.math.BigDecimal;

public record PaymentRequestDto(Long bookingId, String bookingReference, Long userId, BigDecimal amount,
                                String paymentMethod, String cardLastFour, String transactionId) {
}
