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
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordLength.Validator.class)
public @interface PasswordLength {

    String message() default "La contraseña debe tener entre 8 y 72 caracteres";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<PasswordLength, String> {
        @Override
        public boolean isValid(String password, ConstraintValidatorContext context) {
            return password == null
                    || (password.length() >= 8 && password.getBytes(StandardCharsets.UTF_8).length <= 72);
        }
    }
}
