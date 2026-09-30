package com.fitconnect.booking.repository;

import com.fitconnect.booking.model.Booking;
import com.fitconnect.booking.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserIdOrderByBookingDateDesc(Long userId);

    List<Booking> findByStatusOrderByIdAsc(BookingStatus status);

    /** Réservations en attente dont le délai de paiement est dépassé. */
    List<Booking> findByStatusAndPaymentDeadlineBefore(BookingStatus status, LocalDateTime now);

    /** Réservations confirmées dont le cours a lieu dans la fenêtre de rappel et non encore rappelées. */
    List<Booking> findByStatusAndReminderSentFalseAndClassDateBetween(BookingStatus status,
                                                                      LocalDateTime from, LocalDateTime to);

    boolean existsByBookingReference(String bookingReference);
}
