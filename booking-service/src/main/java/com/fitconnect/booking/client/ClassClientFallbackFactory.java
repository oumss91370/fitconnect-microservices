package com.fitconnect.booking.client;

import com.fitconnect.booking.exception.BookingConflictException;
import com.fitconnect.booking.exception.ClassNotFoundException;
import com.fitconnect.booking.exception.NoSpotsAvailableException;
import com.fitconnect.booking.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback du circuit breaker de class-service.
 * - 404 / 409 : erreurs MÉTIER renvoyées par class-service -> on les retransmet telles quelles
 *   (elles ne comptent pas comme des pannes : ignore-exceptions dans config-repo).
 * - autres cas (service arrêté, timeout, circuit ouvert) : 503 Service Unavailable.
 */
@Component
public class ClassClientFallbackFactory implements FallbackFactory<ClassClient> {

    private static final Logger log = LoggerFactory.getLogger(ClassClientFallbackFactory.class);

    @Override
    public ClassClient create(Throwable cause) {
        return new ClassClient() {
            @Override
            public ClassDto getFitnessClass(Long id) {
                throw translate(cause, id, "lecture du cours");
            }

            @Override
            public ClassDto incrementParticipants(Long id, int spots) {
                RuntimeException ex = translate(cause, id, "réservation des places");
                if (RemoteErrors.status(cause) == 409) {
                    throw new NoSpotsAvailableException(RemoteErrors.remoteMessage(cause,
                            "Plus de places disponibles pour ce cours"));
                }
                throw ex;
            }

            @Override
            public ClassDto decrementParticipants(Long id, int spots) {
                throw translate(cause, id, "libération des places");
            }
        };
    }

    private RuntimeException translate(Throwable cause, Long classId, String operation) {
        int status = RemoteErrors.status(cause);
        if (status == 404) {
            return new ClassNotFoundException(RemoteErrors.remoteMessage(cause, "Cours " + classId + " introuvable"));
        }
        if (status == 409) {
            return new BookingConflictException(RemoteErrors.remoteMessage(cause, "Conflit sur le cours " + classId));
        }
        log.error("class-service indisponible pendant la {} du cours {} : {}", operation, classId,
                RemoteErrors.technicalReason(cause));
        return new ServiceUnavailableException("class-service indisponible (" + RemoteErrors.technicalReason(cause)
                + ") : " + operation + " impossible, réessayez plus tard");
    }
}
