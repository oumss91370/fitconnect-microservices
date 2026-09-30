package com.fitconnect.booking.support;

import com.fitconnect.booking.client.PaymentClient;
import com.fitconnect.booking.client.PaymentDto;
import com.fitconnect.booking.client.PaymentRequestDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Double de test de payment-service : même simulation (< 100 € accepté, sinon refusé). */
public class FakePaymentClient implements PaymentClient {

    public final Map<Long, PaymentDto> payments = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    public void reset() {
        payments.clear();
        ids.set(0);
    }

    public PaymentDto givenSuccessfulPayment(Long bookingId, BigDecimal amount) {
        long id = ids.incrementAndGet();
        PaymentDto p = new PaymentDto(id, "PAY-T" + id, bookingId, amount, "CREDIT_CARD", "txn_test",
                LocalDateTime.now(), "SUCCESS", null);
        payments.put(id, p);
        return p;
    }

    @Override
    public PaymentDto processPayment(PaymentRequestDto r) {
        long id = ids.incrementAndGet();
        boolean ok = r.amount().compareTo(new BigDecimal("100")) < 0;
        PaymentDto p = new PaymentDto(id, "PAY-T" + id, r.bookingId(), r.amount(), r.paymentMethod(),
                r.transactionId(), LocalDateTime.now(), ok ? "SUCCESS" : "FAILED", ok ? null : "montant ≥ 100 €");
        payments.put(id, p);
        return p;
    }

    @Override
    public PaymentDto getPaymentByBooking(Long bookingId) {
        return payments.values().stream().filter(p -> p.bookingId().equals(bookingId)).reduce((a, b) -> b).orElseThrow();
    }

    @Override
    public PaymentDto refund(Long paymentId) {
        PaymentDto p = payments.get(paymentId);
        PaymentDto refunded = new PaymentDto(p.id(), p.paymentReference(), p.bookingId(), p.amount(),
                p.paymentMethod(), p.transactionId(), p.paymentDate(), "REFUNDED", null);
        payments.put(paymentId, refunded);
        return refunded;
    }
}
