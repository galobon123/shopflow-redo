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

    /** BCrypt hash; null for accounts created with Google (HU-32). */
    private String password;

    /** HU-31 · Needed to buy. Set by the link in the email, a sign-in code (HU-33) or Google. */
    @Column(nullable = false)
    private boolean emailVerified;

    /** HU-32 · Google's id for the person ("sub"), once they signed in with Google. */
    @Column(unique = true)
    private String googleSub;

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
