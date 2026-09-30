package com.fitconnect.classservice.dto;

import com.fitconnect.classservice.model.ClassCategory;
import com.fitconnect.classservice.model.ClassLevel;
import com.fitconnect.classservice.model.ClassStatus;
import com.fitconnect.classservice.validation.AllowedValues;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Corps de POST /api/classes et PUT /api/classes/{id}. */
public record FitnessClassRequest(
        @NotBlank(message = "le nom est obligatoire")
        @Size(min = 3, message = "le nom doit faire au moins 3 caractères")
        String name,

        @NotBlank(message = "la description est obligatoire")
        String description,

        @NotBlank(message = "l'instructeur est obligatoire")
        String instructor,

        @NotBlank(message = "la salle (gymLocation) est obligatoire")
        String gymLocation,

        @NotNull(message = "la catégorie est obligatoire")
        ClassCategory category,

        @NotNull(message = "le niveau est obligatoire")
        ClassLevel level,

        @NotNull(message = "la durée est obligatoire")
        @AllowedValues(value = {30, 45, 60, 90}, message = "la durée doit valoir 30, 45, 60 ou 90 minutes")
        Integer durationMinutes,

        @NotNull(message = "le nombre maximum de participants est obligatoire")
        @Min(value = 5, message = "maxParticipants doit être ≥ 5")
        @Max(value = 30, message = "maxParticipants doit être ≤ 30")
        Integer maxParticipants,

        @PositiveOrZero(message = "currentParticipants doit être ≥ 0")
        Integer currentParticipants,

        @NotNull(message = "le prix est obligatoire")
        @DecimalMin(value = "5.00", message = "le prix doit être ≥ 5.00")
        @Digits(integer = 8, fraction = 2)
        BigDecimal price,

        @NotNull(message = "la date est obligatoire")
        @FutureOrPresent(message = "la date du cours doit être dans le futur")
        LocalDateTime dateTime,

        ClassStatus status
) {
    @AssertTrue(message = "currentParticipants doit être ≤ maxParticipants")
    public boolean isParticipantsConsistent() {
        return currentParticipants == null || maxParticipants == null || currentParticipants <= maxParticipants;
    }
}
