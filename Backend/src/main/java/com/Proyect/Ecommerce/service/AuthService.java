package com.Proyect.Ecommerce.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.Proyect.Ecommerce.dto.auth.AuthRequestDTO;
import com.Proyect.Ecommerce.dto.auth.AuthResponseDTO;
import com.Proyect.Ecommerce.dto.auth.RegisterRequestDTO;
import com.Proyect.Ecommerce.exception.ConflictException;
import com.Proyect.Ecommerce.model.Role;
import com.Proyect.Ecommerce.model.User;
import com.Proyect.Ecommerce.repository.UserRepository;
import com.Proyect.Ecommerce.security.JwtService;
import com.Proyect.Ecommerce.security.LoginAttemptService;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final LoginAttemptService loginAttempts;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       UserDetailsService userDetailsService,
                       LoginAttemptService loginAttempts) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.loginAttempts = loginAttempts;
    }

    public AuthResponseDTO register(RegisterRequestDTO dto) {
        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new ConflictException("Ya existe una cuenta con ese email.");
        }

        User user = new User();
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole(Role.USER);
        try {
            // Flush now: if a parallel registration took the email in the meantime, the unique
            // constraint fails here and becomes the same 409 as above instead of a 500.
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Ya existe una cuenta con ese email.");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(dto.getEmail());
        String token = jwtService.generateToken(userDetails, user.getId(), user.getName());
        return new AuthResponseDTO(token, user.getEmail(), user.getRole().name());
    }

    /**
     * HU-07 · Wrong email and wrong password give the same 401 (DaoAuthenticationProvider hides which one),
     * and each failure counts towards the per-IP limit.
     */
    public AuthResponseDTO login(AuthRequestDTO dto, String clientIp) {
        loginAttempts.checkAllowed(clientIp, dto.getEmail());
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.getEmail(), dto.getPassword()));
        } catch (BadCredentialsException e) {
            loginAttempts.recordFailure(clientIp, dto.getEmail());
            throw e;
        }
        loginAttempts.reset(clientIp, dto.getEmail());

        UserDetails userDetails = userDetailsService.loadUserByUsername(dto.getEmail());
        User user = userRepository.findByEmail(dto.getEmail()).orElseThrow();
        String token = jwtService.generateToken(userDetails, user.getId(), user.getName());
        return new AuthResponseDTO(token, user.getEmail(), user.getRole().name());
    }
}