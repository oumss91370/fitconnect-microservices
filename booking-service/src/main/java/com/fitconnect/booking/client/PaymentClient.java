package com.fitconnect.booking.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service", contextId = "paymentClient", path = "/api/payments",
        fallbackFactory = PaymentClientFallbackFactory.class, primary = false)
public interface PaymentClient {

    @PostMapping
    PaymentDto processPayment(@RequestBody PaymentRequestDto request);

    @GetMapping("/booking/{bookingId}")
    PaymentDto getPaymentByBooking(@PathVariable("bookingId") Long bookingId);

    @PostMapping("/{id}/refund")
    PaymentDto refund(@PathVariable("id") Long paymentId);
}
