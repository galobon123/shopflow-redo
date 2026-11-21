package com.Proyect.Ecommerce.dto.auth;

import com.Proyect.Ecommerce.model.Emails;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthRequestDTO {
    @NotBlank(message = "Ingresá tu email")
    @Email(message = "El email no es válido")
    private String email;

    @NotBlank(message = "Ingresá tu contraseña")
    private String password;

    /** HU-33 · Optional: the token this browser got when it became trusted. Without it, a code is emailed. */
    private String deviceToken;

    /** Same normalization as the registration, so "Ana@Mail.com" signs in to "ana@mail.com". */
    public void setEmail(String email) {
        this.email = Emails.normalize(email);
    }
}
