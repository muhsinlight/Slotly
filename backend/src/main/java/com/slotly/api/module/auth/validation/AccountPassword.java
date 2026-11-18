package com.slotly.api.module.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({FIELD, METHOD, PARAMETER})
@Retention(RUNTIME)
@NotBlank
@Size(min = 10, message = "Parola en az 10 karakter olmalıdır.")
@Constraint(validatedBy = AccountPassword.BcryptLengthValidator.class)
public @interface AccountPassword {
    String message() default "Parola UTF-8 kodlamasında en fazla 72 bayt olabilir.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class BcryptLengthValidator implements ConstraintValidator<AccountPassword, String> {
        @Override
        public boolean isValid(String password, ConstraintValidatorContext context) {
            return password == null || (password.length() <= 72 && password.getBytes(StandardCharsets.UTF_8).length <= 72);
        }
    }
}
