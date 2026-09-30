package com.fitconnect.payment.web;

import com.fitconnect.payment.dto.PaymentRequest;
import com.fitconnect.payment.dto.PaymentResponse;
import com.fitconnect.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    /** Traite un paiement : 201 Created, avec status SUCCESS ou FAILED dans le corps. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse process(@Valid @RequestBody PaymentRequest request) {
        return service.process(request);
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable Long id) {
        return service.findById(id);
    }

    /** Dernier paiement (ou tentative) d'une réservation. */
    @GetMapping("/booking/{bookingId}")
    public PaymentResponse byBooking(@PathVariable Long bookingId) {
        return service.findByBooking(bookingId);
    }

    @PostMapping("/{id}/refund")
    public PaymentResponse refund(@PathVariable Long id) {
        return service.refund(id);
    }

    @GetMapping("/user/{userId}")
    public List<PaymentResponse> byUser(@PathVariable Long userId) {
        return service.findByUser(userId);
    }
}
