package com.fitconnect.payment.dto;

import com.fitconnect.payment.model.Payment;
import com.fitconnect.payment.model.PaymentMethod;
import com.fitconnect.payment.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(Long id, String paymentReference, Long bookingId, String bookingReference,
                              Long userId, BigDecimal amount, PaymentMethod paymentMethod, String cardLastFour,
                              String transactionId, LocalDateTime paymentDate, PaymentStatus status,
                              String failureReason, LocalDateTime refundDate) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getPaymentReference(), p.getBookingId(), p.getBookingReference(),
                p.getUserId(), p.getAmount(), p.getPaymentMethod(), p.getCardLastFour(), p.getTransactionId(),
                p.getPaymentDate(), p.getStatus(), p.getFailureReason(), p.getRefundDate());
    }
}
