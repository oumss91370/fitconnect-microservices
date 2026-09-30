package com.fitconnect.booking.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

import java.util.Optional;
import java.util.concurrent.TimeoutException;

/** Outils pour interpréter l'erreur reçue par un fallback de circuit breaker. */
final class RemoteErrors {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RemoteErrors() {
    }

    /** Remonte la chaîne des causes pour retrouver la réponse HTTP d'erreur (4xx/5xx). */
    static Optional<FeignException> feignException(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof FeignException fe && fe.status() > 0) return Optional.of(fe);
            if (c.getCause() == c) break;
        }
        return Optional.empty();
    }

    static int status(Throwable t) {
        return feignException(t).map(FeignException::status).orElse(-1);
    }

    /** Message métier renvoyé par le service distant (champ "message" de son ErrorResponse). */
    static String remoteMessage(Throwable t, String defaultMessage) {
        return feignException(t).flatMap(fe -> fe.responseBody())
                .map(bb -> {
                    try {
                        byte[] bytes = new byte[bb.remaining()];
                        bb.duplicate().get(bytes);
                        JsonNode node = MAPPER.readTree(bytes);
                        return node.hasNonNull("message") ? node.get("message").asText() : null;
                    } catch (Exception e) {
                        return null;
                    }
                })
                .orElse(defaultMessage);
    }

    /** Description courte de la panne technique (circuit ouvert, timeout, service injoignable...). */
    static String technicalReason(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof CallNotPermittedException) return "circuit breaker ouvert";
            if (c instanceof TimeoutException) return "délai de réponse dépassé";
            if (c.getCause() == c) break;
        }
        int status = status(t);
        return status > 0 ? "erreur HTTP " + status : "service injoignable (" + t.getClass().getSimpleName() + ")";
    }
}
