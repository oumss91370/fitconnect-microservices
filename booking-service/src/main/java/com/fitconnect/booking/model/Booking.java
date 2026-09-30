package com.fitconnect.booking.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "booking", indexes = {
        @Index(name = "idx_booking_user", columnList = "userId"),
        @Index(name = "idx_booking_status_deadline", columnList = "status,paymentDeadline")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String bookingReference;

    @Column(nullable = false)
    private Long userId;

    /* ---- snapshot de l'utilisateur au moment de la réservation ---- */
    @NotBlank
    @Email
    @Column(nullable = false)
    private String userEmail;

    @NotBlank
    @Column(nullable = false)
    private String userName;

    /* ---- snapshot du cours au moment de la réservation ---- */
    @Column(nullable = false)
    private Long classId;

    private String className;

    private LocalDateTime classDate;

    private String instructor;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @NotNull
    @Min(1)
    @Max(4)
    @Column(nullable = false)
    private Integer numberOfSpots;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private LocalDateTime bookingDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(nullable = false)
    private LocalDateTime paymentDeadline;

    @Column(nullable = false)
    private LocalDateTime cancellationDeadline;

    /* ---- informations complémentaires ---- */
    private Long paymentId;

    private String paymentReference;

    /** Statut du paiement associé : SUCCESS, FAILED, REFUNDED (copie de payment-service). */
    private String paymentStatus;

    /** Motif d'annulation : USER_REQUEST, PAYMENT_TIMEOUT, PAYMENT_FAILED. */
    private String cancellationReason;

    private LocalDateTime cancelledAt;

    @Builder.Default
    private boolean reminderSent = false;

    /** Évite qu'une confirmation et une annulation simultanées s'écrasent. */
    @Version
    private Long version;
}
