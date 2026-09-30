package com.fitconnect.booking.dto;

import jakarta.validation.constraints.*;

/** Corps de POST /api/bookings. */
public record CreateBookingRequest(
        @NotNull(message = "userId est obligatoire") Long userId,
        @NotBlank(message = "userEmail est obligatoire") @Email(message = "userEmail invalide") String userEmail,
        @NotBlank(message = "userName est obligatoire") String userName,
        @NotNull(message = "classId est obligatoire") Long classId,
        @NotNull(message = "numberOfSpots est obligatoire")
        @Min(value = 1, message = "numberOfSpots doit être compris entre 1 et 4")
        @Max(value = 4, message = "numberOfSpots doit être compris entre 1 et 4") Integer numberOfSpots) {
}
