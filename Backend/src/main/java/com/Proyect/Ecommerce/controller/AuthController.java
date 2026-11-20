package com.Proyect.Ecommerce.controller;

import com.Proyect.Ecommerce.dto.auth.AuthConfigDTO;
import com.Proyect.Ecommerce.dto.auth.AuthRequestDTO;
import com.Proyect.Ecommerce.dto.auth.AuthResponseDTO;
import com.Proyect.Ecommerce.dto.auth.ChallengeRequestDTO;
import com.Proyect.Ecommerce.dto.auth.GoogleLoginRequestDTO;
import com.Proyect.Ecommerce.dto.auth.LoginChallengeDTO;
import com.Proyect.Ecommerce.dto.auth.LoginCodeRequestDTO;
import com.Proyect.Ecommerce.dto.auth.RegisterRequestDTO;
import com.Proyect.Ecommerce.dto.auth.VerifyEmailRequestDTO;
import com.Proyect.Ecommerce.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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

    /**
     * 200 with the session, or 202 with a challenge when this browser isn't trusted yet (HU-33).
     * The client IP is the real one behind Nginx/Caddy: see server.forward-headers-strategy.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequestDTO dto, HttpServletRequest request) {
        AuthService.LoginResult result = authService.login(dto, request.getRemoteAddr());
        return result.session() != null
                ? ResponseEntity.ok(result.session())
                : ResponseEntity.status(HttpStatus.ACCEPTED).body(result.challenge());
    }

    @PostMapping("/login/code")
    public ResponseEntity<AuthResponseDTO> confirmLogin(@Valid @RequestBody LoginCodeRequestDTO dto) {
        return ResponseEntity.ok(authService.confirmLogin(dto.challengeId(), dto.code()));
    }

    @PostMapping("/login/code/resend")
    public ResponseEntity<LoginChallengeDTO> resendLoginCode(@Valid @RequestBody ChallengeRequestDTO dto) {
        return ResponseEntity.ok(authService.resendLoginCode(dto.challengeId()));
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponseDTO> google(@Valid @RequestBody GoogleLoginRequestDTO dto) {
        return ResponseEntity.ok(authService.loginWithGoogle(dto.credential()));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<AuthResponseDTO> verifyEmail(@Valid @RequestBody VerifyEmailRequestDTO dto) {
        return ResponseEntity.ok(authService.verifyEmail(dto.token()));
    }

    /** Needs a session (see SecurityConfig): it's the logged-in person asking for their own link. */
    @PostMapping("/verify-email/resend")
    public ResponseEntity<AuthResponseDTO> resendVerification(Authentication auth) {
        return ResponseEntity.ok(authService.resendVerification((Long) auth.getCredentials()));
    }

    @GetMapping("/config")
    public AuthConfigDTO config() {
        return new AuthConfigDTO(authService.googleClientId());
    }
}
