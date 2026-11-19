package com.Proyect.Ecommerce.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Ingresá el nombre")
    @Column(nullable = false)
    private String name;

    @Email
    @NotBlank(message = "Ingresá el email")
    @Column(nullable = false, unique = true)
    private String email;

    @NotBlank(message = "Ingresá la contraseña")
    @Column(nullable = false)
    private String password;

    @NotNull(message = "Elegí un rol")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
