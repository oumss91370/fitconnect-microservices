package com.fitconnect.booking.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** Client Feign vers class-service (résolu via Eureka), protégé par un circuit breaker Resilience4j. */
@FeignClient(name = "class-service", contextId = "classClient", path = "/api/classes",
        fallbackFactory = ClassClientFallbackFactory.class, primary = false)
public interface ClassClient {

    @GetMapping("/{id}")
    ClassDto getFitnessClass(@PathVariable("id") Long id);

    @PatchMapping("/{id}/increment")
    ClassDto incrementParticipants(@PathVariable("id") Long id, @RequestParam("spots") int spots);

    @PatchMapping("/{id}/decrement")
    ClassDto decrementParticipants(@PathVariable("id") Long id, @RequestParam("spots") int spots);
}
