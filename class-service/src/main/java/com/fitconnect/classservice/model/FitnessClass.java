package com.fitconnect.classservice.model;

import com.fitconnect.classservice.exception.ClassNotBookableException;
import com.fitconnect.classservice.exception.InvalidParticipantsException;
import com.fitconnect.classservice.exception.NoSpotsAvailableException;
import com.fitconnect.classservice.validation.AllowedValues;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fitness_class")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FitnessClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(min = 3)
    @Column(nullable = false)
    private String name;

    @NotBlank
    @Column(nullable = false, length = 2000)
    private String description;

    @NotBlank
    @Column(nullable = false)
    private String instructor;

    @NotBlank
    @Column(nullable = false)
    private String gymLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClassCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClassLevel level;

    @NotNull
    @AllowedValues({30, 45, 60, 90})
    @Column(nullable = false)
    private Integer durationMinutes;

    @NotNull
    @Min(5)
    @Max(30)
    @Column(nullable = false)
    private Integer maxParticipants;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false)
    @Builder.Default
    private Integer currentParticipants = 0;

    @NotNull
    @DecimalMin("5.00")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime dateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ClassStatus status = ClassStatus.SCHEDULED;

    /**
     * Verrouillage optimiste : Hibernate ajoute "WHERE version = ?" à chaque UPDATE.
     * Si deux transactions modifient le même cours en parallèle, la seconde échoue
     * (OptimisticLockException) au lieu d'écraser la première : pas de surréservation.
     */
    @Version
    private Long version;

    public int getAvailableSpots() {
        return maxParticipants - currentParticipants;
    }

    /** Réserve des places (appelé par booking-service via PATCH /increment). */
    public void incrementParticipants(int spots) {
        if (spots <= 0) {
            throw new InvalidParticipantsException("Le nombre de places doit être positif");
        }
        if (status != ClassStatus.SCHEDULED) {
            throw new ClassNotBookableException("Le cours " + id + " n'est pas réservable (statut " + status + ")");
        }
        if (dateTime.isBefore(LocalDateTime.now())) {
            throw new ClassNotBookableException("Le cours " + id + " est déjà passé");
        }
        if (this.currentParticipants + spots > this.maxParticipants) {
            throw new NoSpotsAvailableException("Plus de places disponibles pour ce cours ("
                    + getAvailableSpots() + " restante(s), " + spots + " demandée(s))");
        }
        this.currentParticipants += spots;
    }

    /** Libère des places (annulation / expiration d'une réservation). */
    public void decrementParticipants(int spots) {
        if (spots <= 0) {
            throw new InvalidParticipantsException("Le nombre de places doit être positif");
        }
        if (this.currentParticipants - spots < 0) {
            throw new InvalidParticipantsException("Impossible de libérer " + spots
                    + " place(s) : seulement " + currentParticipants + " participant(s)");
        }
        this.currentParticipants -= spots;
    }
}
