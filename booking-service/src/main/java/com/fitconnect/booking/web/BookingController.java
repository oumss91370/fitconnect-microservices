package com.fitconnect.booking.web;

import com.fitconnect.booking.BookingProperties;
import com.fitconnect.booking.dto.BookingResponse;
import com.fitconnect.booking.dto.ConfirmBookingRequest;
import com.fitconnect.booking.dto.CreateBookingRequest;
import com.fitconnect.booking.exception.BookingNotFoundException;
import com.fitconnect.booking.model.BookingStatus;
import com.fitconnect.booking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService service;
    private final BookingProperties properties;

    public BookingController(BookingService service, BookingProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @GetMapping
    public List<BookingResponse> list(@RequestParam(required = false) BookingStatus status) {
        return service.findAll(status);
    }

    @GetMapping("/{id}")
    public BookingResponse get(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/user/{userId}")
    public List<BookingResponse> byUser(@PathVariable Long userId) {
        return service.findByUser(userId);
    }

    /** Saga de réservation : 201 PENDING_PAYMENT, 409 si plus de places, 404 si cours inconnu, 503 si panne. */
    @PostMapping
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody CreateBookingRequest request) {
        BookingResponse created = service.createBooking(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** Paiement : 200 CONFIRMED (ou 200 CANCELLED si le paiement est refusé), 409 si expiré / mauvais statut. */
    @PatchMapping("/{id}/confirm")
    public BookingResponse confirm(@PathVariable Long id, @Valid @RequestBody ConfirmBookingRequest request) {
        return service.confirmBooking(id, request);
    }

    /** Annulation : 200 CANCELLED (+ remboursement si payé), 409 si moins de 24 h avant le cours. */
    @PatchMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable Long id) {
        return service.cancelBooking(id);
    }

    @PatchMapping("/{id}/complete")
    public BookingResponse complete(@PathVariable Long id) {
        return service.completeBooking(id);
    }

    /** Réservations en attente dont le délai de paiement est dépassé (celles que le scheduler va annuler). */
    @GetMapping("/expired")
    public List<BookingResponse> expired() {
        return service.findExpired();
    }

    /** Déclenche immédiatement le traitement du scheduler d'expiration (administration / démo). */
    @PostMapping("/expired/cancel")
    public List<BookingResponse> cancelExpiredNow() {
        return service.cancelExpiredBookings();
    }

    /** Démo uniquement (fitconnect.booking.demo-endpoints-enabled) : force l'expiration du délai de paiement. */
    @PatchMapping("/{id}/simulate-expiration")
    public BookingResponse simulateExpiration(@PathVariable Long id) {
        if (!properties.demoEndpointsEnabled()) {
            throw new BookingNotFoundException("Endpoint de démonstration désactivé");
        }
        return service.simulateExpiration(id);
    }
}
