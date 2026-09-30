package com.fitconnect.payment.repository;

import com.fitconnect.payment.model.Payment;
import com.fitconnect.payment.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findFirstByBookingIdOrderByIdDesc(Long bookingId);

    List<Payment> findByUserIdOrderByIdDesc(Long userId);

    boolean existsByBookingIdAndStatus(Long bookingId, PaymentStatus status);

    boolean existsByPaymentReference(String paymentReference);
}
