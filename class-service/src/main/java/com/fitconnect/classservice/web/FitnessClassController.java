package com.fitconnect.classservice.web;

import com.fitconnect.classservice.dto.ClassSearchCriteria;
import com.fitconnect.classservice.dto.FitnessClassRequest;
import com.fitconnect.classservice.dto.FitnessClassResponse;
import com.fitconnect.classservice.model.ClassCategory;
import com.fitconnect.classservice.model.ClassLevel;
import com.fitconnect.classservice.model.ClassStatus;
import com.fitconnect.classservice.service.FitnessClassService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/classes")
@Validated
public class FitnessClassController {

    private final FitnessClassService service;

    public FitnessClassController(FitnessClassService service) {
        this.service = service;
    }

    /** GET /api/classes?category=YOGA&level=BEGINNER&dateFrom=...&dateTo=...&location=...&instructor=...&page=0&size=10&sort=dateTime,asc */
    @GetMapping
    public Page<FitnessClassResponse> list(
            @RequestParam(required = false) ClassCategory category,
            @RequestParam(required = false) ClassLevel level,
            @RequestParam(required = false) ClassStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String instructor,
            @PageableDefault(size = 10, sort = "dateTime", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.search(new ClassSearchCriteria(category, level, status, dateFrom, dateTo,
                location, instructor, false), pageable);
    }

    /**
     * GET /api/classes/search : recherche orientée client (par défaut seulement les cours
     * programmés, à venir et non complets). ?date=2026-10-05 cible un jour précis.
     */
    @GetMapping("/search")
    public Page<FitnessClassResponse> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) ClassCategory category,
            @RequestParam(required = false) ClassLevel level,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String instructor,
            @RequestParam(defaultValue = "true") boolean availableOnly,
            @PageableDefault(size = 10, sort = "dateTime", direction = Sort.Direction.ASC) Pageable pageable) {
        LocalDate from = date != null ? date : dateFrom;
        LocalDate to = date != null ? date : dateTo;
        return service.search(new ClassSearchCriteria(category, level, null, from, to,
                location, instructor, availableOnly), pageable);
    }

    @GetMapping("/{id}")
    public FitnessClassResponse get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<FitnessClassResponse> create(@Valid @RequestBody FitnessClassRequest request) {
        FitnessClassResponse created = service.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public FitnessClassResponse update(@PathVariable Long id, @Valid @RequestBody FitnessClassRequest request) {
        return service.update(id, request);
    }

    /** 204 si le cours a été supprimé, 200 + cours CANCELLED s'il avait déjà des participants. */
    @DeleteMapping("/{id}")
    public ResponseEntity<FitnessClassResponse> delete(@PathVariable Long id) {
        return service.deleteOrCancel(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NO_CONTENT).build());
    }

    /** Appelé par booking-service : réserve des places (409 si plus assez de places). */
    @PatchMapping("/{id}/increment")
    public FitnessClassResponse increment(@PathVariable Long id,
                                          @RequestParam(defaultValue = "1") @Min(1) int spots) {
        return service.incrementParticipants(id, spots);
    }

    /** Appelé par booking-service : libère des places. */
    @PatchMapping("/{id}/decrement")
    public FitnessClassResponse decrement(@PathVariable Long id,
                                          @RequestParam(defaultValue = "1") @Min(1) int spots) {
        return service.decrementParticipants(id, spots);
    }
}
