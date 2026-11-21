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

    @NotBlank(message = "Ingresá una contraseña")
    @PasswordLength
    private String password;

    /** Jackson calls this before validation, so @Email sees the clean value. */
    public void setEmail(String email) {
        this.email = Emails.normalize(email);
    }
}
