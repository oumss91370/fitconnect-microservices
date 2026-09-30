package com.fitconnect.classservice.model;

import com.fitconnect.classservice.exception.ClassNotBookableException;
import com.fitconnect.classservice.exception.InvalidParticipantsException;
import com.fitconnect.classservice.exception.NoSpotsAvailableException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests unitaires des règles métier portées par l'entité (sans Spring). */
class FitnessClassTest {

    private static FitnessClass fitnessClass(int max, int current) {
        return FitnessClass.builder().id(1L).name("Yoga").description("d").instructor("Marie")
                .gymLocation("Paris").category(ClassCategory.YOGA).level(ClassLevel.BEGINNER)
                .durationMinutes(60).maxParticipants(max).currentParticipants(current)
                .price(new BigDecimal("15.00")).dateTime(LocalDateTime.now().plusDays(3))
                .status(ClassStatus.SCHEDULED).build();
    }

    @Test
    void shouldIncrementParticipants_whenSpotsAvailable() {
        FitnessClass c = fitnessClass(10, 5);
        c.incrementParticipants(2);
        assertThat(c.getCurrentParticipants()).isEqualTo(7);
        assertThat(c.getAvailableSpots()).isEqualTo(3);
    }

    @Test
    void shouldThrowException_whenNoSpotsAvailable() {
        // Given: class with 10 spots, 9 current participants
        FitnessClass c = fitnessClass(10, 9);
        // When: booking 2 spots  /  Then: NoSpotsAvailableException, rien n'est modifié
        assertThatThrownBy(() -> c.incrementParticipants(2))
                .isInstanceOf(NoSpotsAvailableException.class)
                .hasMessageContaining("Plus de places disponibles");
        assertThat(c.getCurrentParticipants()).isEqualTo(9);
    }

    @Test
    void shouldFillLastSpotExactly() {
        FitnessClass c = fitnessClass(10, 8);
        c.incrementParticipants(2);
        assertThat(c.getAvailableSpots()).isZero();
    }

    @Test
    void shouldRefuseBooking_whenClassCancelled() {
        FitnessClass c = fitnessClass(10, 0);
        c.setStatus(ClassStatus.CANCELLED);
        assertThatThrownBy(() -> c.incrementParticipants(1)).isInstanceOf(ClassNotBookableException.class);
    }

    @Test
    void shouldDecrementParticipants_andRefuseNegativeCount() {
        FitnessClass c = fitnessClass(10, 3);
        c.decrementParticipants(2);
        assertThat(c.getCurrentParticipants()).isEqualTo(1);
        assertThatThrownBy(() -> c.decrementParticipants(2)).isInstanceOf(InvalidParticipantsException.class);
    }
}
