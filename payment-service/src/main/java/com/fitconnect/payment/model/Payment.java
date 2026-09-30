package com.fitconnect.payment.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String paymentReference;

    @Column(nullable = false)
    private Long bookingId;

    private String bookingReference;

    @Column(nullable = false)
    private Long userId;

    @NotNull
    @DecimalMin("0.00")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    @Pattern(regexp = "\\d{4}")
    @Column(length = 4)
    private String cardLastFour;

    private String transactionId;

    private LocalDateTime paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    /** Motif de refus (paiement FAILED) — information complémentaire. */
    private String failureReason;

    /** Date du remboursement (paiement REFUNDED) — information complémentaire. */
    private LocalDateTime refundDate;
}
