package com.fitconnect.booking.service;

import com.fitconnect.booking.client.NotificationClient;
import com.fitconnect.booking.client.NotificationRequestDto;
import com.fitconnect.booking.model.Booking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Construit les messages et appelle notification-service (best effort : une panne ne bloque pas la saga). */
@Component
public class BookingNotifier {

    private static final Logger log = LoggerFactory.getLogger(BookingNotifier.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm", Locale.FRANCE);

    private final NotificationClient client;

    public BookingNotifier(NotificationClient client) {
        this.client = client;
    }

    public void bookingPendingPayment(Booking b) {
        send(b, "BOOKING_CONFIRMATION", "Réservation " + b.getBookingReference() + " en attente de paiement",
                "Votre réservation est en attente de paiement. Payez avant le " + FMT.format(b.getPaymentDeadline())
                        + ". (" + b.getUserName() + " : " + b.getNumberOfSpots() + " place(s) pour \"" + b.getClassName()
                        + "\" du " + FMT.format(b.getClassDate()) + ", total " + b.getTotalAmount() + " €)");
    }

    public void paymentConfirmed(Booking b) {
        send(b, "PAYMENT_CONFIRMATION", "Paiement reçu - réservation " + b.getBookingReference() + " confirmée",
                "Bonjour " + b.getUserName() + ", nous avons reçu votre paiement de " + b.getTotalAmount()
                        + " € (" + b.getPaymentReference() + "). Rendez-vous le " + FMT.format(b.getClassDate())
                        + " pour \"" + b.getClassName() + "\" avec " + b.getInstructor()
                        + ". Annulation gratuite jusqu'au " + FMT.format(b.getCancellationDeadline()) + ".");
    }

    public void bookingCancelled(Booking b, String reason) {
        send(b, "BOOKING_CANCELLED", "Réservation " + b.getBookingReference() + " annulée",
                "Bonjour " + b.getUserName() + ", votre réservation pour \"" + b.getClassName() + "\" du "
                        + FMT.format(b.getClassDate()) + " a été annulée : " + reason + ".");
    }

    public void reminder(Booking b) {
        send(b, "BOOKING_REMINDER", "Rappel : " + b.getClassName() + " demain",
                "Bonjour " + b.getUserName() + ", rappel : votre cours \"" + b.getClassName() + "\" avec "
                        + b.getInstructor() + " a lieu le " + FMT.format(b.getClassDate()) + " ("
                        + b.getNumberOfSpots() + " place(s)).");
    }

    private void send(Booking b, String type, String subject, String content) {
        try {
            client.send(new NotificationRequestDto(b.getUserId(), b.getUserEmail(), type, subject, content));
        } catch (RuntimeException e) {
            log.warn("Notification {} non envoyée pour {} : {}", type, b.getBookingReference(), e.getMessage());
        }
    }
}
