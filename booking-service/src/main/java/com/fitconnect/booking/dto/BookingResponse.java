package com.fitconnect.booking.dto;

import com.fitconnect.booking.model.Booking;
import com.fitconnect.booking.model.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BookingResponse(Long id, String bookingReference, Long userId, String userEmail, String userName,
                              Long classId, String className, LocalDateTime classDate, String instructor,
                              BigDecimal price, Integer numberOfSpots, BigDecimal totalAmount,
                              LocalDateTime bookingDate, BookingStatus status, LocalDateTime paymentDeadline,
                              LocalDateTime cancellationDeadline, Long paymentId, String paymentReference, String paymentStatus,
                              String cancellationReason, LocalDateTime cancelledAt) {

    public static BookingResponse from(Booking b) {
        return new BookingResponse(b.getId(), b.getBookingReference(), b.getUserId(), b.getUserEmail(),
                b.getUserName(), b.getClassId(), b.getClassName(), b.getClassDate(), b.getInstructor(),
                b.getPrice(), b.getNumberOfSpots(), b.getTotalAmount(), b.getBookingDate(), b.getStatus(),
                b.getPaymentDeadline(), b.getCancellationDeadline(), b.getPaymentId(), b.getPaymentReference(), b.getPaymentStatus(),
                b.getCancellationReason(), b.getCancelledAt());
    }
}
