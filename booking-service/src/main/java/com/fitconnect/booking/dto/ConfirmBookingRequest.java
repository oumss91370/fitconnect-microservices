package com.fitconnect.booking.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Corps de PATCH /api/bookings/{id}/confirm. */
public record ConfirmBookingRequest(
        @NotBlank(message = "paymentMethod est obligatoire")
        @Pattern(regexp = "CREDIT_CARD|DEBIT_CARD|PAYPAL|STRIPE",
                message = "paymentMethod doit valoir CREDIT_CARD, DEBIT_CARD, PAYPAL ou STRIPE") String paymentMethod,
        @Pattern(regexp = "\\d{4}", message = "cardLastFour doit contenir exactement 4 chiffres") String cardLastFour,
        String transactionId) {

    @AssertTrue(message = "cardLastFour est obligatoire pour un paiement par carte")
    public boolean isCardInfoPresent() {
        return !("CREDIT_CARD".equals(paymentMethod) || "DEBIT_CARD".equals(paymentMethod)) || cardLastFour != null;
    }
}
