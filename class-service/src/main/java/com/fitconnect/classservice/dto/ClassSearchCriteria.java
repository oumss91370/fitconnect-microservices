package com.fitconnect.classservice.dto;

import com.fitconnect.classservice.model.ClassCategory;
import com.fitconnect.classservice.model.ClassLevel;
import com.fitconnect.classservice.model.ClassStatus;

import java.time.LocalDate;

/** Filtres combinables : ?category=YOGA&level=BEGINNER&dateFrom=...&dateTo=...&location=Paris&instructor=Marie */
public record ClassSearchCriteria(ClassCategory category, ClassLevel level, ClassStatus status,
                                  LocalDate dateFrom, LocalDate dateTo,
                                  String location, String instructor, boolean availableOnly) {
}
