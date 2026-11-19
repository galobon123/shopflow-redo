package com.Proyect.Ecommerce.dto.auth;

import com.Proyect.Ecommerce.model.Emails;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequestDTO {
    @NotBlank(message = "Ingresá tu nombre")
    @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
    private String name;

    @NotBlank(message = "Ingresá tu email")
    @Email(message = "El email no es válido")
    private String email;

    // BCrypt only uses the first 72 bytes and Spring Security rejects longer passwords.
    @NotBlank(message = "Ingresá una contraseña")
    @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
    private String password;

    /** Jackson calls this before validation, so @Email sees the clean value. */
    public void setEmail(String email) {
        this.email = Emails.normalize(email);
    }
}
