package com.fitconnect.booking.integration;

import com.fitconnect.booking.client.ClassDto;
import com.fitconnect.booking.model.Booking;
import com.fitconnect.booking.model.BookingStatus;
import com.fitconnect.booking.repository.BookingRepository;
import com.fitconnect.booking.scheduler.BookingScheduler;
import com.fitconnect.booking.support.FakeClassClient;
import com.fitconnect.booking.support.FakeNotificationClient;
import com.fitconnect.booking.support.FakePaymentClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests d'intégration du booking-service : contexte Spring complet, vraie base H2, vrais
 * contrôleurs / services / scheduler. Seuls les 3 services distants (class, payment,
 * notification) sont remplacés par des doubles en mémoire au comportement identique.
 * (Le flux avec les vrais services est vérifié par la collection Postman, cf. README.)
 */
@SpringBootTest
@AutoConfigureMockMvc
class BookingFlowIntegrationTest {

    @TestConfiguration
    static class RemoteServicesDoubles {
        @Bean @Primary FakeClassClient fakeClassClient() { return new FakeClassClient(); }
        @Bean @Primary FakePaymentClient fakePaymentClient() { return new FakePaymentClient(); }
        @Bean @Primary FakeNotificationClient fakeNotificationClient() { return new FakeNotificationClient(); }
    }

    @Autowired MockMvc mvc;
    @Autowired FakeClassClient classService;
    @Autowired FakePaymentClient paymentService;
    @Autowired FakeNotificationClient notificationService;
    @Autowired BookingRepository bookingRepository;
    @Autowired BookingScheduler scheduler;

    @BeforeEach
    void reset() {
        classService.reset();
        paymentService.reset();
        notificationService.sent.clear();
    }

    @Test
    @Transactional
    void shouldCompleteFullBookingFlow() throws Exception {
        // 1. Create class (10 places, 15 €)
        ClassDto yoga = classService.createClass("Yoga Vinyasa", 10, 0, new BigDecimal("15.00"),
                LocalDateTime.now().plusDays(7));

        // 2. Create booking (2 places)
        String created = mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content("""
                        {"userId":1,"userEmail":"john@example.com","userName":"John Doe","classId":%d,"numberOfSpots":2}
                        """.formatted(yoga.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.totalAmount").value(30.00))
                .andReturn().getResponse().getContentAsString();
        Integer bookingId = JsonPath.read(created, "$.id");

        // 3. Confirm payment
        mvc.perform(patch("/api/bookings/{id}/confirm", bookingId).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentMethod":"CREDIT_CARD","cardLastFour":"1234","transactionId":"txn_123456"}
                                """))
                .andExpect(status().isOk())
                // 4. Verify booking status = CONFIRMED
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentReference").exists());
        assertThat(bookingRepository.findById(bookingId.longValue()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);

        // 5. Verify spots decreased (10 -> 8 places disponibles)
        assertThat(classService.getFitnessClass(yoga.id()).availableSpots()).isEqualTo(8);

        // 6. Verify notification sent
        assertThat(notificationService.types()).containsExactly("BOOKING_CONFIRMATION", "PAYMENT_CONFIRMATION");
        assertThat(notificationService.sent.get(1).email()).isEqualTo("john@example.com");

        mvc.perform(get("/api/bookings/user/1")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void shouldCancelExpiredBookings() throws Exception {
        // 1. Create booking with paymentDeadline in past (3 places réservées côté class-service)
        ClassDto zumba = classService.createClass("Zumba", 20, 0, new BigDecimal("12.00"), LocalDateTime.now().plusDays(2));
        classService.incrementParticipants(zumba.id(), 3);
        Booking expired = bookingRepository.save(Booking.builder()
                .bookingReference("BK-EXP01").userId(7L).userEmail("late@example.com").userName("Late Payer")
                .classId(zumba.id()).className("Zumba").classDate(zumba.dateTime()).instructor("Marie Dupont")
                .price(new BigDecimal("12.00")).numberOfSpots(3).totalAmount(new BigDecimal("36.00"))
                .bookingDate(LocalDateTime.now().minusHours(2)).status(BookingStatus.PENDING_PAYMENT)
                .paymentDeadline(LocalDateTime.now().minusHours(1))
                .cancellationDeadline(zumba.dateTime().minusHours(24)).build());
        mvc.perform(get("/api/bookings/expired")).andExpect(jsonPath("$[0].bookingReference").value("BK-EXP01"));

        // 2. Run scheduler
        scheduler.cancelExpiredBookings();

        // 3. Verify status = CANCELLED
        Booking after = bookingRepository.findById(expired.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(after.getCancellationReason()).isEqualTo("PAYMENT_TIMEOUT");

        // 4. Verify spots restored
        assertThat(classService.currentParticipants(zumba.id())).isZero();
        assertThat(notificationService.types()).containsExactly("BOOKING_CANCELLED");
        mvc.perform(get("/api/bookings/expired")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldSendReminderOnce_forConfirmedBookingWithin24h() {
        ClassDto boxe = classService.createClass("Boxe", 10, 1, new BigDecimal("10.00"), LocalDateTime.now().plusHours(20));
        bookingRepository.save(Booking.builder()
                .bookingReference("BK-REM01").userId(8L).userEmail("remind@example.com").userName("Rem")
                .classId(boxe.id()).className("Boxe").classDate(boxe.dateTime()).instructor("Marie Dupont")
                .price(new BigDecimal("10.00")).numberOfSpots(1).totalAmount(new BigDecimal("10.00"))
                .bookingDate(LocalDateTime.now().minusDays(1)).status(BookingStatus.CONFIRMED)
                .paymentDeadline(LocalDateTime.now().minusHours(23))
                .cancellationDeadline(boxe.dateTime().minusHours(24)).build());

        scheduler.sendReminders();
        scheduler.sendReminders();   // second passage : pas de doublon

        assertThat(notificationService.types()).containsExactly("BOOKING_REMINDER");
    }
}
