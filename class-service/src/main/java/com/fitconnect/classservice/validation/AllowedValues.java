package com.fitconnect.classservice.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;
import java.util.Arrays;

/** Contrainte : la valeur entière doit appartenir à une liste (ex. durée 30, 45, 60 ou 90 minutes). */
@Documented
@Constraint(validatedBy = AllowedValues.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowedValues {
    int[] value();

    String message() default "valeur non autorisée";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<AllowedValues, Integer> {
        private int[] allowed;

        @Override
        public void initialize(AllowedValues annotation) {
            this.allowed = annotation.value();
        }

        @Override
        public boolean isValid(Integer value, ConstraintValidatorContext context) {
            return value == null || Arrays.stream(allowed).anyMatch(v -> v == value);
        }
    }
}
