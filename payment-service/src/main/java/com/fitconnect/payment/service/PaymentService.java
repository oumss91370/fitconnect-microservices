package com.fitconnect.payment.service;

import com.fitconnect.payment.dto.PaymentRequest;
import com.fitconnect.payment.dto.PaymentResponse;
import com.fitconnect.payment.exception.PaymentConflictException;
import com.fitconnect.payment.exception.ResourceNotFoundException;
import com.fitconnect.payment.model.Payment;
import com.fitconnect.payment.model.PaymentStatus;
import com.fitconnect.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PaymentRepository repository;
    private final BigDecimal maxAcceptedAmount;

    public PaymentService(PaymentRepository repository,
                          @Value("${fitconnect.payment.max-accepted-amount:100.00}") BigDecimal maxAcceptedAmount) {
        this.repository = repository;
        this.maxAcceptedAmount = maxAcceptedAmount;
    }

    /**
     * Simulation de la passerelle bancaire :
     * montant < 100 € -> SUCCESS ; montant >= 100 € -> FAILED.
     * Le paiement est toujours enregistré (trace des tentatives refusées).
     */
    @Transactional
    public PaymentResponse process(PaymentRequest r) {
        if (repository.existsByBookingIdAndStatus(r.bookingId(), PaymentStatus.SUCCESS)) {
            throw new PaymentConflictException("La réservation " + r.bookingId() + " est déjà payée");
        }
        boolean accepted = r.amount().compareTo(maxAcceptedAmount) < 0;
        Payment p = Payment.builder()
                .paymentReference(newReference())
                .bookingId(r.bookingId())
                .bookingReference(r.bookingReference())
                .userId(r.userId())
                .amount(r.amount())
                .paymentMethod(r.paymentMethod())
                .cardLastFour(r.paymentMethod().isCard() ? r.cardLastFour() : null)
                .transactionId(r.transactionId() != null && !r.transactionId().isBlank()
                        ? r.transactionId() : "txn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .paymentDate(LocalDateTime.now())
                .status(accepted ? PaymentStatus.SUCCESS : PaymentStatus.FAILED)
                .failureReason(accepted ? null : "Paiement refusé par la banque (simulation : montant ≥ "
                        + maxAcceptedAmount + " €)")
                .build();
        p = repository.save(p);
        log.info("Paiement {} pour la réservation {} : {} € -> {}", p.getPaymentReference(), r.bookingReference(),
                r.amount(), p.getStatus());
        return PaymentResponse.from(p);
    }

    @Transactional(readOnly = true)
    public PaymentResponse findByBooking(Long bookingId) {
        return repository.findFirstByBookingIdOrderByIdDesc(bookingId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun paiement pour la réservation " + bookingId));
    }

    @Transactional(readOnly = true)
    public PaymentResponse findById(Long id) {
        return PaymentResponse.from(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> findByUser(Long userId) {
        return repository.findByUserIdOrderByIdDesc(userId).stream().map(PaymentResponse::from).toList();
    }

    /**
     * Remboursement : seul un paiement SUCCESS peut être remboursé.
     * La règle des 24 h avant le cours est contrôlée par booking-service (cancellationDeadline)
     * avant d'appeler cet endpoint.
     */
    @Transactional
    public PaymentResponse refund(Long id) {
        Payment p = getOrThrow(id);
        if (p.getStatus() != PaymentStatus.SUCCESS) {
            throw new PaymentConflictException("Le paiement " + p.getPaymentReference()
                    + " ne peut pas être remboursé (statut " + p.getStatus() + ")");
        }
        p.setStatus(PaymentStatus.REFUNDED);
        p.setRefundDate(LocalDateTime.now());
        log.info("Paiement {} remboursé ({} €)", p.getPaymentReference(), p.getAmount());
        return PaymentResponse.from(p);
    }

    private Payment getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Paiement " + id + " introuvable"));
    }

    private String newReference() {
        String ref;
        do {
            StringBuilder sb = new StringBuilder("PAY-");
            for (int i = 0; i < 5; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            ref = sb.toString();
        } while (repository.existsByPaymentReference(ref));
        return ref;
    }
}
