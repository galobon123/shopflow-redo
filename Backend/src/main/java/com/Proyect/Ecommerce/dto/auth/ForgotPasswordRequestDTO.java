package com.Proyect.Ecommerce.dto.auth;

import com.Proyect.Ecommerce.model.Emails;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordRequestDTO {
    @NotBlank(message = "Ingresá tu email")
    @Email(message = "El email no es válido")
    private String email;

    /** Same normalization as the registration, so "Ana@Mail.com" finds "ana@mail.com". */
    public void setEmail(String email) {
        this.email = Emails.normalize(email);
    }
}
