package com.fitconnect.booking.scheduler;

import com.fitconnect.booking.dto.BookingResponse;
import com.fitconnect.booking.service.BookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BookingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BookingScheduler.class);

    private final BookingService bookingService;

    public BookingScheduler(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * Toutes les 5 minutes : PENDING_PAYMENT dont paymentDeadline < now()
     * -> CANCELLED, places libérées dans class-service, notification BOOKING_CANCELLED.
     */
    @Scheduled(fixedRateString = "${fitconnect.scheduler.expiration-rate:PT5M}",
            initialDelayString = "${fitconnect.scheduler.initial-delay:PT30S}")
    public void cancelExpiredBookings() {
        List<BookingResponse> cancelled = bookingService.cancelExpiredBookings();
        if (!cancelled.isEmpty()) {
            log.info("[Scheduler] {} réservation(s) expirée(s) annulée(s) : {}", cancelled.size(),
                    cancelled.stream().map(BookingResponse::bookingReference).toList());
        }
    }

    /**
     * Rappel 24 h avant : les réservations CONFIRMED dont le cours commence dans les prochaines 24 h
     * et qui n'ont pas encore reçu de rappel -> notification BOOKING_REMINDER (une seule fois).
     */
    @Scheduled(fixedRateString = "${fitconnect.scheduler.reminder-rate:PT5M}",
            initialDelayString = "${fitconnect.scheduler.initial-delay:PT30S}")
    public void sendReminders() {
        int sent = bookingService.sendReminders();
        if (sent > 0) {
            log.info("[Scheduler] {} rappel(s) envoyé(s)", sent);
        }
    }
}
