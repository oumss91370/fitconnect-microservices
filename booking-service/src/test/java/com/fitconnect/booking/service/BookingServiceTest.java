package com.fitconnect.booking.service;

import com.fitconnect.booking.BookingProperties;
import com.fitconnect.booking.client.ClassDto;
import com.fitconnect.booking.client.PaymentDto;
import com.fitconnect.booking.dto.BookingResponse;
import com.fitconnect.booking.dto.ConfirmBookingRequest;
import com.fitconnect.booking.dto.CreateBookingRequest;
import com.fitconnect.booking.exception.BookingConflictException;
import com.fitconnect.booking.exception.NoSpotsAvailableException;
import com.fitconnect.booking.model.Booking;
import com.fitconnect.booking.model.BookingStatus;
import com.fitconnect.booking.repository.BookingRepository;
import com.fitconnect.booking.support.FakeClassClient;
import com.fitconnect.booking.support.FakeNotificationClient;
import com.fitconnect.booking.support.FakePaymentClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires de l'orchestrateur (saga) : repository mocké (Mockito),
 * services distants remplacés par des doubles en mémoire.
 */
class BookingServiceTest {

    private final FakeClassClient classClient = new FakeClassClient();
    private final FakePaymentClient paymentClient = new FakePaymentClient();
    private final FakeNotificationClient notificationClient = new FakeNotificationClient();
    private final Map<Long, Booking> db = new HashMap<>();
    private BookingRepository repository;
    private BookingService service;

    @BeforeEach
    void setUp() {
        repository = mock(BookingRepository.class);
        AtomicLong ids = new AtomicLong();
        when(repository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            if (b.getId() == null) b.setId(ids.incrementAndGet());
            db.put(b.getId(), b);
            return b;
        });
        when(repository.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(db.get(inv.<Long>getArgument(0))));
        when(repository.existsByBookingReference(any())).thenReturn(false);
        BookingProperties props = new BookingProperties(Duration.ofHours(1), Duration.ofHours(24), Duration.ofHours(24), true);
        service = new BookingService(repository, classClient, paymentClient, new BookingNotifier(notificationClient),
                props, Clock.systemDefaultZone());
    }

    private CreateBookingRequest request(Long classId, int spots) {
        return new CreateBookingRequest(1L, "john@example.com", "John Doe", classId, spots);
    }

    @Test
    void shouldCreateBooking_whenSpotsAvailable() {
        // Given: class with 10 spots, 5 current participants
        ClassDto c = classClient.createClass("Yoga", 10, 5, new BigDecimal("15.00"), LocalDateTime.now().plusDays(3));

        // When: booking 2 spots
        BookingResponse booking = service.createBooking(request(c.id(), 2));

        // Then: status = PENDING_PAYMENT, spots = 7
        assertThat(booking.status()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(classClient.currentParticipants(c.id())).isEqualTo(7);
        assertThat(booking.bookingReference()).matches("BK-[A-Z0-9]{5}");
        assertThat(booking.totalAmount()).isEqualByComparingTo("30.00");
        assertThat(booking.className()).isEqualTo("Yoga");                                   // snapshot
        assertThat(booking.paymentDeadline()).isBetween(LocalDateTime.now().plusMinutes(59), LocalDateTime.now().plusMinutes(61));
        assertThat(booking.cancellationDeadline()).isEqualTo(c.dateTime().minusHours(24));
        assertThat(notificationClient.types()).containsExactly("BOOKING_CONFIRMATION");
    }

    @Test
    void shouldThrowException_whenNoSpotsAvailable() {
        // Given: class with 10 spots, 9 current participants
        ClassDto c = classClient.createClass("Yoga", 10, 9, new BigDecimal("15.00"), LocalDateTime.now().plusDays(3));

        // When: booking 2 spots  /  Then: NoSpotsAvailableException
        assertThatThrownBy(() -> service.createBooking(request(c.id(), 2)))
                .isInstanceOf(NoSpotsAvailableException.class);
        assertThat(classClient.currentParticipants(c.id())).isEqualTo(9);
        verify(repository, never()).save(any());
        assertThat(notificationClient.sent).isEmpty();
    }

    @Test
    void shouldThrowException_whenSpotsTakenConcurrentlyBetweenCheckAndIncrement() {
        // Cas 2 de l'énoncé : places disponibles à l'étape 1, mais quelqu'un réserve avant l'étape 2
        ClassDto c = classClient.createClass("Yoga", 10, 5, new BigDecimal("15.00"), LocalDateTime.now().plusDays(3));
        classClient.stolenSpotsBeforeIncrement = 4;

        assertThatThrownBy(() -> service.createBooking(request(c.id(), 2)))
                .isInstanceOf(NoSpotsAvailableException.class);
        // Compensation : aucune réservation créée, aucune place prise par cette requête
        verify(repository, never()).save(any());
        assertThat(classClient.currentParticipants(c.id())).isEqualTo(9);
    }

    @Test
    void shouldCancelBookingAndRefund_whenWithinDeadline() {
        // Given: confirmed booking, cancellationDeadline in future
        ClassDto c = classClient.createClass("Yoga", 10, 5, new BigDecimal("15.00"), LocalDateTime.now().plusDays(3));
        BookingResponse created = service.createBooking(request(c.id(), 2));                 // 7 participants
        BookingResponse confirmed = service.confirmBooking(created.id(),
                new ConfirmBookingRequest("CREDIT_CARD", "1234", "txn_123456"));
        assertThat(confirmed.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(confirmed.cancellationDeadline()).isAfter(LocalDateTime.now());

        // When: cancel booking
        BookingResponse cancelled = service.cancelBooking(created.id());

        // Then: status = CANCELLED, payment refunded, spots decreased
        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
        PaymentDto payment = paymentClient.payments.get(confirmed.paymentId());
        assertThat(payment.status()).isEqualTo("REFUNDED");
        assertThat(classClient.currentParticipants(c.id())).isEqualTo(5);
        assertThat(notificationClient.types())
                .containsExactly("BOOKING_CONFIRMATION", "PAYMENT_CONFIRMATION", "BOOKING_CANCELLED");
    }

    @Test
    void shouldRefuseCancellation_whenLessThan24hBeforeClass() {
        ClassDto c = classClient.createClass("Yoga", 10, 0, new BigDecimal("15.00"), LocalDateTime.now().plusHours(5));
        BookingResponse created = service.createBooking(request(c.id(), 1));

        assertThatThrownBy(() -> service.cancelBooking(created.id()))
                .isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("Annulation non autorisée");
        assertThat(db.get(created.id()).getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(classClient.currentParticipants(c.id())).isEqualTo(1);
    }

    @Test
    void shouldCancelAndReleaseSpots_whenPaymentRefused() {
        // 4 places x 30 € = 120 € >= 100 € -> refus simulé -> compensation, réponse 200 avec statut CANCELLED
        ClassDto c = classClient.createClass("CrossFit", 10, 0, new BigDecimal("30.00"), LocalDateTime.now().plusDays(3));
        BookingResponse created = service.createBooking(request(c.id(), 4));

        BookingResponse result = service.confirmBooking(created.id(), new ConfirmBookingRequest("PAYPAL", null, null));

        assertThat(result.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(result.paymentStatus()).isEqualTo("FAILED");
        assertThat(result.cancellationReason()).isEqualTo(BookingService.REASON_PAYMENT_FAILED);
        assertThat(classClient.currentParticipants(c.id())).isZero();
        assertThat(notificationClient.types()).containsExactly("BOOKING_CONFIRMATION", "BOOKING_CANCELLED");
    }

    @Test
    void shouldRefundPayment_whenBookingChangedDuringPayment() {
        // La réservation est annulée (scheduler / autre requête) pendant l'appel à payment-service :
        // l'enregistrement CONFIRMED échoue sur le contrôle de version -> le paiement est remboursé.
        ClassDto c = classClient.createClass("Yoga", 10, 0, new BigDecimal("15.00"), LocalDateTime.now().plusDays(3));
        BookingResponse created = service.createBooking(request(c.id(), 1));
        when(repository.save(argThat(b -> b.getStatus() == BookingStatus.CONFIRMED)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Booking.class, created.id()));

        assertThatThrownBy(() -> service.confirmBooking(created.id(), new ConfirmBookingRequest("STRIPE", null, null)))
                .isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("remboursé");
        assertThat(paymentClient.payments.values()).singleElement()
                .satisfies(p -> assertThat(p.status()).isEqualTo("REFUNDED"));
    }

    @Test
    void shouldNotReleaseSpotsTwice_whenConcurrentCancellationLosesVersionCheck() {
        // Deux annulations simultanées : celle qui perd le contrôle de version ne doit PAS libérer les places.
        ClassDto c = classClient.createClass("Yoga", 10, 0, new BigDecimal("15.00"), LocalDateTime.now().plusDays(3));
        BookingResponse created = service.createBooking(request(c.id(), 3));
        when(repository.save(argThat(b -> b.getStatus() == BookingStatus.CANCELLED)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Booking.class, created.id()));

        assertThatThrownBy(() -> service.cancelBooking(created.id()))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(classClient.currentParticipants(c.id())).isEqualTo(3);   // places toujours réservées
    }

    @Test
    void shouldRejectPayment_whenPaymentDeadlinePassed() {
        ClassDto c = classClient.createClass("Yoga", 10, 0, new BigDecimal("15.00"), LocalDateTime.now().plusDays(3));
        BookingResponse created = service.createBooking(request(c.id(), 2));
        db.get(created.id()).setPaymentDeadline(LocalDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> service.confirmBooking(created.id(), new ConfirmBookingRequest("STRIPE", null, null)))
                .isInstanceOf(BookingConflictException.class)
                .hasMessageContaining("Paiement expiré");
        assertThat(paymentClient.payments).isEmpty();                     // aucun débit
        assertThat(db.get(created.id()).getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(classClient.currentParticipants(c.id())).isZero();
    }
}
