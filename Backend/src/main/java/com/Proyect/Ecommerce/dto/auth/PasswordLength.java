package com.Proyect.Ecommerce.dto.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * HU-06 + HU-09 · At least 8 characters and at most 72 UTF-8 bytes: BCrypt only uses the first 72 bytes and
 * Spring Security rejects longer passwords, so "ñ" counts twice. Null is left to @NotBlank.
 * The messages avoid numbers of characters: a person typing "ñ" can't count bytes.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordLength.Validator.class)
public @interface PasswordLength {

    String message() default "Tiene que tener al menos 8 caracteres.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<PasswordLength, String> {
        @Override
        public boolean isValid(String password, ConstraintValidatorContext context) {
            if (password == null) return true;
            if (password.length() < 8) return false;
            if (password.getBytes(StandardCharsets.UTF_8).length <= 72) return true;
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Es demasiado larga. Probá con una más corta.").addConstraintViolation();
            return false;
        }
    }
}
