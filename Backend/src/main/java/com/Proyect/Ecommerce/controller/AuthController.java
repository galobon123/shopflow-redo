package com.Proyect.Ecommerce.controller;

import com.Proyect.Ecommerce.dto.auth.AuthRequestDTO;
import com.Proyect.Ecommerce.dto.auth.AuthResponseDTO;
import com.Proyect.Ecommerce.dto.auth.RegisterRequestDTO;
import com.Proyect.Ecommerce.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@Valid @RequestBody RegisterRequestDTO dto) {
        return ResponseEntity.ok(authService.register(dto));
    }

    /** The client IP is the real one behind Nginx/Caddy: see server.forward-headers-strategy. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody AuthRequestDTO dto, HttpServletRequest request) {
        return ResponseEntity.ok(authService.login(dto, request.getRemoteAddr()));
    }
}
