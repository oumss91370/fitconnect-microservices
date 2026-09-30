package com.fitconnect.payment.dto;

import com.fitconnect.payment.model.PaymentMethod;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/** Corps de POST /api/payments (envoyé par booking-service lors du PATCH /api/bookings/{id}/confirm). */
public record PaymentRequest(
        @NotNull(message = "bookingId est obligatoire") Long bookingId,
        String bookingReference,
        @NotNull(message = "userId est obligatoire") Long userId,
        @NotNull(message = "le montant est obligatoire")
        @DecimalMin(value = "0.00", message = "le montant doit être ≥ 0") BigDecimal amount,
        @NotNull(message = "le moyen de paiement est obligatoire") PaymentMethod paymentMethod,
        @Pattern(regexp = "\\d{4}", message = "cardLastFour doit contenir exactement 4 chiffres") String cardLastFour,
        String transactionId) {

    @AssertTrue(message = "cardLastFour est obligatoire pour un paiement par carte")
    public boolean isCardInfoPresent() {
        return paymentMethod == null || !paymentMethod.isCard() || cardLastFour != null;
    }
}
