package com.fitconnect.classservice.dto;

import com.fitconnect.classservice.model.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FitnessClassResponse(
        Long id, String name, String description, String instructor, String gymLocation,
        ClassCategory category, ClassLevel level, Integer durationMinutes,
        Integer maxParticipants, Integer currentParticipants, Integer availableSpots,
        BigDecimal price, LocalDateTime dateTime, ClassStatus status, Long version) {

    public static FitnessClassResponse from(FitnessClass c) {
        return new FitnessClassResponse(c.getId(), c.getName(), c.getDescription(), c.getInstructor(),
                c.getGymLocation(), c.getCategory(), c.getLevel(), c.getDurationMinutes(),
                c.getMaxParticipants(), c.getCurrentParticipants(), c.getAvailableSpots(),
                c.getPrice(), c.getDateTime(), c.getStatus(), c.getVersion());
    }
}
