package com.Proyect.Ecommerce.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.Proyect.Ecommerce.model.Role;
import com.Proyect.Ecommerce.model.User;
import com.Proyect.Ecommerce.repository.UserRepository;

/** HU-02 · Admin inicial desde variables de entorno. */
class AdminBootstrapTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    private AdminBootstrap bootstrap(String email, String password) {
        return new AdminBootstrap(users, encoder, email, password, "Admin");
    }

    @Test
    void createsAdminWithHashedPassword() {
        when(users.findByEmail("admin@shopflow.local")).thenReturn(Optional.empty());

        bootstrap(" admin@shopflow.local ", "una-clave-larga").run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertEquals("admin@shopflow.local", saved.getValue().getEmail());
        assertEquals(Role.ADMIN, saved.getValue().getRole());
        assertEquals(true, encoder.matches("una-clave-larga", saved.getValue().getPassword()));
    }

    @Test
    void doesNothingWhenAdminAlreadyExists() {
        when(users.findByEmail("admin@shopflow.local")).thenReturn(Optional.of(new User()));

        bootstrap("admin@shopflow.local", "una-clave-larga").run(null);

        verify(users, never()).save(any());
    }

    @Test
    void doesNothingWhenVariablesAreEmpty() {
        bootstrap("", "").run(null);

        verify(users, never()).findByEmail(any());
        verify(users, never()).save(any());
    }

    @Test
    void refusesShortPassword() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> bootstrap("admin@shopflow.local", "corta").run(null));
        assertEquals("ADMIN_PASSWORD debe tener al menos 12 caracteres.", error.getMessage());
    }
}
