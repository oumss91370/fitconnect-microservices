package com.fitconnect.booking.support;

import com.fitconnect.booking.client.ClassClient;
import com.fitconnect.booking.client.ClassDto;
import com.fitconnect.booking.exception.ClassNotFoundException;
import com.fitconnect.booking.exception.NoSpotsAvailableException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Double de test de class-service : mêmes règles (places, 409) mais en mémoire. */
public class FakeClassClient implements ClassClient {

    private final Map<Long, ClassDto> classes = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();
    /** Permet de simuler une réservation concurrente entre GET et PATCH /increment. */
    public volatile int stolenSpotsBeforeIncrement = 0;

    public ClassDto createClass(String name, int max, int current, BigDecimal price, LocalDateTime date) {
        long id = ids.incrementAndGet();
        ClassDto c = new ClassDto(id, name, "Marie Dupont", "Paris 11", price, date, "SCHEDULED", max, current, max - current);
        classes.put(id, c);
        return c;
    }

    public int currentParticipants(Long id) {
        return classes.get(id).currentParticipants();
    }

    public void reset() {
        classes.clear();
        ids.set(0);
        stolenSpotsBeforeIncrement = 0;
    }

    @Override
    public ClassDto getFitnessClass(Long id) {
        ClassDto c = classes.get(id);
        if (c == null) throw new ClassNotFoundException("Cours " + id + " introuvable");
        return c;
    }

    @Override
    public synchronized ClassDto incrementParticipants(Long id, int spots) {
        if (stolenSpotsBeforeIncrement > 0) {
            update(id, stolenSpotsBeforeIncrement);
            stolenSpotsBeforeIncrement = 0;
        }
        ClassDto c = getFitnessClass(id);
        if (c.currentParticipants() + spots > c.maxParticipants()) {
            throw new NoSpotsAvailableException("Plus de places disponibles pour ce cours");
        }
        return update(id, spots);
    }

    @Override
    public synchronized ClassDto decrementParticipants(Long id, int spots) {
        return update(id, -spots);
    }

    private ClassDto update(Long id, int delta) {
        ClassDto c = getFitnessClass(id);
        int current = c.currentParticipants() + delta;
        ClassDto updated = new ClassDto(c.id(), c.name(), c.instructor(), c.gymLocation(), c.price(), c.dateTime(),
                c.status(), c.maxParticipants(), current, c.maxParticipants() - current);
        classes.put(id, updated);
        return updated;
    }
}
