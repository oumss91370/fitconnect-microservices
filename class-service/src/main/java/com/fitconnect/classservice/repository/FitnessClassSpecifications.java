package com.fitconnect.classservice.repository;

import com.fitconnect.classservice.dto.ClassSearchCriteria;
import com.fitconnect.classservice.model.ClassStatus;
import com.fitconnect.classservice.model.FitnessClass;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class FitnessClassSpecifications {

    private FitnessClassSpecifications() {
    }

    public static Specification<FitnessClass> matching(ClassSearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (c.category() != null) p.add(cb.equal(root.get("category"), c.category()));
            if (c.level() != null) p.add(cb.equal(root.get("level"), c.level()));
            if (c.status() != null) p.add(cb.equal(root.get("status"), c.status()));
            if (c.dateFrom() != null) p.add(cb.greaterThanOrEqualTo(root.get("dateTime"), c.dateFrom().atStartOfDay()));
            if (c.dateTo() != null) p.add(cb.lessThan(root.get("dateTime"), c.dateTo().plusDays(1).atStartOfDay()));
            if (hasText(c.location())) p.add(cb.like(cb.lower(root.get("gymLocation")), like(c.location())));
            if (hasText(c.instructor())) p.add(cb.like(cb.lower(root.get("instructor")), like(c.instructor())));
            if (c.availableOnly()) {
                p.add(cb.equal(root.get("status"), ClassStatus.SCHEDULED));
                p.add(cb.greaterThan(root.get("dateTime"), LocalDateTime.now()));
                p.add(cb.lessThan(root.get("currentParticipants"), root.get("maxParticipants")));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String like(String s) {
        return "%" + s.trim().toLowerCase() + "%";
    }
}
