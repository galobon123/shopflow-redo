package com.Proyect.Ecommerce.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.Proyect.Ecommerce.model.Role;
import com.Proyect.Ecommerce.model.User;
import com.Proyect.Ecommerce.repository.UserRepository;

/**
 * HU-02 · Creates the first admin from ADMIN_EMAIL / ADMIN_PASSWORD on startup.
 * Done in code instead of a migration so the password is hashed with the app's encoder
 * and never lives in SQL. Does nothing if the variables are empty or the email already exists.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    static final int MIN_PASSWORD_LENGTH = 12;

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String name;

    public AdminBootstrap(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${app.admin.email:}") String email,
                          @Value("${app.admin.password:}") String password,
                          @Value("${app.admin.name:Admin}") String name) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email.trim();
        this.password = password;
        this.name = name;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isEmpty() || password.isEmpty()) {
            log.info("ADMIN_EMAIL/ADMIN_PASSWORD vacíos: no se crea admin inicial.");
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres.");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            return;
        }

        User admin = new User();
        admin.setEmail(email);
        admin.setName(name);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);
        log.info("Admin inicial creado: {}", email);
    }
}
