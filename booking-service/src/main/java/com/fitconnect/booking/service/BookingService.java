package com.fitconnect.booking.service;

import com.fitconnect.booking.BookingProperties;
import com.fitconnect.booking.client.*;
import com.fitconnect.booking.dto.BookingResponse;
import com.fitconnect.booking.dto.ConfirmBookingRequest;
import com.fitconnect.booking.dto.CreateBookingRequest;
import com.fitconnect.booking.exception.*;
import com.fitconnect.booking.model.Booking;
import com.fitconnect.booking.model.BookingStatus;
import com.fitconnect.booking.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

/**
 * Orchestrateur de la saga "Reserve Now, Pay Later".
 * <p>
 * Chaque étape appelle un autre microservice ; en cas d'échec d'une étape, les étapes déjà
 * réalisées sont compensées (ex. places libérées si la réservation ne peut pas être enregistrée
 * ou si le paiement est refusé). Les méthodes ne sont volontairement pas @Transactional :
 * on ne garde pas de transaction base de données ouverte pendant des appels HTTP distants.
 */
@Service
public class BookingService {

    public static final String REASON_USER = "USER_REQUEST";
    public static final String REASON_TIMEOUT = "PAYMENT_TIMEOUT";
    public static final String REASON_PAYMENT_FAILED = "PAYMENT_FAILED";

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BookingRepository repository;
    private final ClassClient classClient;
    private final PaymentClient paymentClient;
    private final BookingNotifier notifier;
    private final BookingProperties properties;
    private final Clock clock;

    public BookingService(BookingRepository repository, ClassClient classClient, PaymentClient paymentClient,
                          BookingNotifier notifier, BookingProperties properties, Clock clock) {
        this.repository = repository;
        this.classClient = classClient;
        this.paymentClient = paymentClient;
        this.notifier = notifier;
        this.properties = properties;
        this.clock = clock;
    }

    /* =========================================================================================
     * Cas 1 / Cas 2 : création d'une réservation
     * ========================================================================================= */
    public BookingResponse createBooking(CreateBookingRequest request) {
        int spots = request.numberOfSpots();
        LocalDateTime now = now();

        // Étape 1 : vérification du cours + snapshot
        ClassDto fitnessClass = classClient.getFitnessClass(request.classId());
        if (!"SCHEDULED".equals(fitnessClass.status())) {
            throw new BookingConflictException("Le cours " + fitnessClass.id() + " n'est pas réservable (statut "
                    + fitnessClass.status() + ")");
        }
        if (!fitnessClass.dateTime().isAfter(now)) {
            throw new BookingConflictException("Le cours " + fitnessClass.id() + " a déjà commencé ou est terminé");
        }
        if (fitnessClass.availableSpots() != null && fitnessClass.availableSpots() < spots) {
            throw new NoSpotsAvailableException("Plus de places disponibles pour ce cours ("
                    + fitnessClass.availableSpots() + " restante(s), " + spots + " demandée(s))");
        }

        // Étape 2 : réservation des places (verrouillage optimiste côté class-service, 409 si complet)
        classClient.incrementParticipants(fitnessClass.id(), spots);

        // Étape 3 : création de la réservation — compensation si l'enregistrement échoue
        Booking booking;
        try {
            LocalDateTime paymentDeadline = now.plus(properties.paymentTimeout());
            if (paymentDeadline.isAfter(fitnessClass.dateTime())) {
                paymentDeadline = fitnessClass.dateTime(); // on ne peut pas payer après le début du cours
            }
            booking = repository.save(Booking.builder()
                    .bookingReference(newReference())
                    .userId(request.userId())
                    .userEmail(request.userEmail())
                    .userName(request.userName())
                    .classId(fitnessClass.id())
                    .className(fitnessClass.name())
                    .classDate(fitnessClass.dateTime())
                    .instructor(fitnessClass.instructor())
                    .price(fitnessClass.price())
                    .numberOfSpots(spots)
                    .totalAmount(fitnessClass.price().multiply(BigDecimal.valueOf(spots)))
                    .bookingDate(now)
                    .status(BookingStatus.PENDING_PAYMENT)
                    .paymentDeadline(paymentDeadline)
                    .cancellationDeadline(fitnessClass.dateTime().minus(properties.cancellationNotice()))
                    .build());
        } catch (RuntimeException e) {
            log.error("Échec d'enregistrement de la réservation, compensation : libération de {} place(s)", spots, e);
            releaseSpotsQuietly(fitnessClass.id(), spots, "compensation création");
            throw e;
        }
        log.info("Réservation {} créée : {} place(s) sur le cours {} ({} €), en attente de paiement",
                booking.getBookingReference(), spots, booking.getClassId(), booking.getTotalAmount());

        // Étape 4 : notification (best effort)
        notifier.bookingPendingPayment(booking);

        // Étape 5 : 201 Created, statut PENDING_PAYMENT
        return BookingResponse.from(booking);
    }

    /* =========================================================================================
     * Cas 3 : paiement et confirmation
     * ========================================================================================= */
    public BookingResponse confirmBooking(Long id, ConfirmBookingRequest request) {
        Booking booking = getOrThrow(id);

        // Étape 1 : vérifications
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BookingConflictException("La réservation " + booking.getBookingReference()
                    + " n'est pas en attente de paiement (statut " + booking.getStatus() + ")");
        }
        if (now().isAfter(booking.getPaymentDeadline())) {
            cancelAndRelease(booking, REASON_TIMEOUT, "délai de paiement dépassé");
            throw new BookingConflictException("Paiement expiré : le délai de paiement ("
                    + booking.getPaymentDeadline() + ") est dépassé. La réservation "
                    + booking.getBookingReference() + " a été annulée et les places libérées");
        }

        // Étape 2 : traitement du paiement
        PaymentDto payment = paymentClient.processPayment(new PaymentRequestDto(booking.getId(),
                booking.getBookingReference(), booking.getUserId(), booking.getTotalAmount(),
                request.paymentMethod(), request.cardLastFour(), request.transactionId()));

        // Étape 3 : mise à jour de la réservation
        booking.setPaymentId(payment.id());
        booking.setPaymentReference(payment.paymentReference());
        booking.setPaymentStatus(payment.status());
        if (!payment.isSuccess()) {
            // Paiement refusé : compensation -> annulation + libération des places (200, statut CANCELLED)
            log.warn("Paiement {} refusé pour {} : {}", payment.paymentReference(), booking.getBookingReference(),
                    payment.failureReason());
            Booking cancelled = cancelAndRelease(booking, REASON_PAYMENT_FAILED, "paiement refusé ("
                    + (payment.failureReason() != null ? payment.failureReason() : "refus bancaire") + ")");
            return BookingResponse.from(cancelled);
        }
        booking.setStatus(BookingStatus.CONFIRMED);
        try {
            booking = repository.save(booking);
        } catch (ObjectOptimisticLockingFailureException e) {
            // La réservation a été annulée / expirée pendant l'appel au paiement : on rembourse (compensation)
            refundQuietly(payment, booking.getBookingReference());
            throw new BookingConflictException("La réservation " + booking.getBookingReference()
                    + " a été modifiée pendant le paiement (annulée ou expirée) : le paiement "
                    + payment.paymentReference() + " a été remboursé");
        }
        log.info("Réservation {} confirmée (paiement {})", booking.getBookingReference(), payment.paymentReference());

        // Étape 4 : notification de confirmation
        notifier.paymentConfirmed(booking);
        return BookingResponse.from(booking);
    }

    /* =========================================================================================
     * Cas 4 : annulation par l'utilisateur
     * ========================================================================================= */
    public BookingResponse cancelBooking(Long id) {
        Booking booking = getOrThrow(id);

        // Étape 1 : vérifications
        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.COMPLETED
                || booking.getStatus() == BookingStatus.NO_SHOW) {
            throw new BookingConflictException("La réservation " + booking.getBookingReference()
                    + " ne peut pas être annulée (statut " + booking.getStatus() + ")");
        }
        if (now().isAfter(booking.getCancellationDeadline())) {
            throw new BookingConflictException("Annulation non autorisée : le cours a lieu dans moins de "
                    + properties.cancellationNotice().toHours() + " h (limite dépassée depuis le "
                    + booking.getCancellationDeadline() + ")");
        }

        // Étape 2 : remboursement si la réservation était payée
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            Long paymentId = booking.getPaymentId() != null ? booking.getPaymentId()
                    : paymentClient.getPaymentByBooking(booking.getId()).id();
            PaymentDto refund = paymentClient.refund(paymentId);
            booking.setPaymentStatus(refund.status());
            log.info("Réservation {} : paiement {} remboursé", booking.getBookingReference(), refund.paymentReference());
        }

        // Étapes 2 bis / 3 / 4 : statut CANCELLED, libération des places, notification
        Booking cancelled = cancelAndRelease(booking, REASON_USER, "annulation à votre demande"
                + ("REFUNDED".equals(booking.getPaymentStatus()) ? ", vous allez être remboursé(e)" : ""));
        return BookingResponse.from(cancelled);
    }

    public BookingResponse completeBooking(Long id) {
        Booking booking = getOrThrow(id);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BookingConflictException("Seule une réservation CONFIRMED peut être terminée (statut actuel "
                    + booking.getStatus() + ")");
        }
        booking.setStatus(BookingStatus.COMPLETED);
        return BookingResponse.from(repository.save(booking));
    }

    /* =========================================================================================
     * Traitements du scheduler
     * ========================================================================================= */

    /** Annule les réservations PENDING_PAYMENT dont le délai de paiement est dépassé. */
    public List<BookingResponse> cancelExpiredBookings() {
        List<Booking> expired = repository.findByStatusAndPaymentDeadlineBefore(BookingStatus.PENDING_PAYMENT, now());
        for (Booking b : expired) {
            try {
                cancelAndRelease(b, REASON_TIMEOUT, "paiement non reçu avant le " + b.getPaymentDeadline());
                log.info("Réservation {} expirée et annulée", b.getBookingReference());
            } catch (RuntimeException e) {
                log.error("Expiration de {} impossible pour l'instant : {}", b.getBookingReference(), e.getMessage());
            }
        }
        return expired.stream().filter(b -> b.getStatus() == BookingStatus.CANCELLED).map(BookingResponse::from).toList();
    }

    /** Envoie un rappel pour les cours confirmés qui commencent dans les prochaines 24 h. */
    public int sendReminders() {
        LocalDateTime now = now();
        List<Booking> toRemind = repository.findByStatusAndReminderSentFalseAndClassDateBetween(
                BookingStatus.CONFIRMED, now, now.plus(properties.reminderBefore()));
        for (Booking b : toRemind) {
            notifier.reminder(b);
            b.setReminderSent(true);
            repository.save(b);
        }
        return toRemind.size();
    }

    /* =========================================================================================
     * Lectures
     * ========================================================================================= */
    public List<BookingResponse> findAll(BookingStatus status) {
        List<Booking> list = status == null ? repository.findAll() : repository.findByStatusOrderByIdAsc(status);
        return list.stream().map(BookingResponse::from).toList();
    }

    public BookingResponse findById(Long id) {
        return BookingResponse.from(getOrThrow(id));
    }

    public List<BookingResponse> findByUser(Long userId) {
        return repository.findByUserIdOrderByBookingDateDesc(userId).stream().map(BookingResponse::from).toList();
    }

    public List<BookingResponse> findExpired() {
        return repository.findByStatusAndPaymentDeadlineBefore(BookingStatus.PENDING_PAYMENT, now()).stream()
                .map(BookingResponse::from).toList();
    }

    /** Démo / tests : place la date limite de paiement dans le passé. */
    public BookingResponse simulateExpiration(Long id) {
        Booking booking = getOrThrow(id);
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BookingConflictException("Seule une réservation PENDING_PAYMENT peut expirer");
        }
        booking.setPaymentDeadline(now().minusMinutes(1));
        return BookingResponse.from(repository.save(booking));
    }

    /* ========================================================================================= */

    /**
     * Passe la réservation en CANCELLED, PUIS libère les places et notifie l'utilisateur.
     * L'enregistrement est fait en premier : grâce au @Version de Booking, si deux annulations
     * (utilisateur + scheduler par ex.) arrivent en même temps, une seule passe et les places
     * ne sont libérées qu'une fois (l'autre reçoit 409).
     */
    private Booking cancelAndRelease(Booking booking, String reason, String humanReason) {
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancellationReason(reason);
        booking.setCancelledAt(now());
        Booking saved = repository.save(booking);
        releaseSpotsQuietly(saved.getClassId(), saved.getNumberOfSpots(), saved.getBookingReference());
        notifier.bookingCancelled(saved, humanReason);
        return saved;
    }

    private void refundQuietly(PaymentDto payment, String bookingReference) {
        try {
            paymentClient.refund(payment.id());
        } catch (RuntimeException e) {
            log.error("[A RÉCONCILIER] remboursement du paiement {} ({}) impossible : {}",
                    payment.paymentReference(), bookingReference, e.getMessage());
        }
    }

    /**
     * Libère les places côté class-service. Si class-service est indisponible, la réservation est
     * tout de même annulée : l'écart est journalisé pour une réconciliation ultérieure.
     */
    private void releaseSpotsQuietly(Long classId, int spots, String context) {
        try {
            classClient.decrementParticipants(classId, spots);
        } catch (RuntimeException e) {
            log.error("[A RÉCONCILIER] impossible de libérer {} place(s) du cours {} ({}) : {}",
                    spots, classId, context, e.getMessage());
        }
    }

    private Booking getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException("Réservation " + id + " introuvable"));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String newReference() {
        Supplier<String> gen = () -> {
            StringBuilder sb = new StringBuilder("BK-");
            for (int i = 0; i < 5; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            return sb.toString();
        };
        String ref;
        do {
            ref = gen.get();
        } while (repository.existsByBookingReference(ref));
        return ref;
    }
}
