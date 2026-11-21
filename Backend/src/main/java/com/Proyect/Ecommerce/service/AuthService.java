package com.Proyect.Ecommerce.service;

import java.time.Instant;

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
import com.Proyect.Ecommerce.dto.auth.LoginChallengeDTO;
import com.Proyect.Ecommerce.dto.auth.RegisterRequestDTO;
import com.Proyect.Ecommerce.exception.ConflictException;
import com.Proyect.Ecommerce.exception.NotFoundException;
import com.Proyect.Ecommerce.model.Role;
import com.Proyect.Ecommerce.model.User;
import com.Proyect.Ecommerce.repository.UserRepository;
import com.Proyect.Ecommerce.security.DeviceName;
import com.Proyect.Ecommerce.security.EmailVerificationService;
import com.Proyect.Ecommerce.security.GoogleTokenVerifier;
import com.Proyect.Ecommerce.security.GoogleTokenVerifier.GoogleIdentity;
import com.Proyect.Ecommerce.security.JwtService;
import com.Proyect.Ecommerce.security.LoginAttemptService;
import com.Proyect.Ecommerce.security.LoginChallengeService;
import com.Proyect.Ecommerce.security.LoginChallengeService.Challenge;
import com.Proyect.Ecommerce.security.PasswordResetService;
import com.Proyect.Ecommerce.security.SessionService;
import com.Proyect.Ecommerce.security.TrustedDeviceService;

@Service
public class AuthService {

    /** What a password sign-in ends in: a session, or (HU-33, new device) a code to confirm first. */
    public record LoginResult(AuthResponseDTO session, LoginChallengeDTO challenge) {
    }

    private static final int MAX_NAME_LENGTH = 100;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final LoginAttemptService loginAttempts;
    private final LoginChallengeService challenges;
    private final TrustedDeviceService trustedDevices;
    private final EmailVerificationService verifications;
    private final GoogleTokenVerifier google;
    private final MailService mail;
    private final SessionService sessions;
    private final PasswordResetService passwordResets;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       UserDetailsService userDetailsService,
                       LoginAttemptService loginAttempts,
                       LoginChallengeService challenges,
                       TrustedDeviceService trustedDevices,
                       EmailVerificationService verifications,
                       GoogleTokenVerifier google,
                       MailService mail,
                       SessionService sessions,
                       PasswordResetService passwordResets) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.loginAttempts = loginAttempts;
        this.challenges = challenges;
        this.trustedDevices = trustedDevices;
        this.verifications = verifications;
        this.google = google;
        this.mail = mail;
        this.sessions = sessions;
        this.passwordResets = passwordResets;
    }

    /**
     * HU-06 + HU-31 · The account starts unverified and gets the confirmation link by email.
     * The browser that registered is trusted (HU-33): the person is already in, asking for a code
     * at their next sign-in from the same place would only annoy.
     */
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

        mail.sendVerificationLink(user.getEmail(), user.getName(), verifications.newLink(user.getId()));
        return session(user, trustedDevices.trust(user.getId()));
    }

    /**
     * HU-07 · Wrong email and wrong password give the same 401 (DaoAuthenticationProvider hides which one),
     * and each failure counts towards the per-IP limit; the one that locks the login warns the owner by email.
     * HU-33 · The right password from a browser that isn't trusted yet gets a code by email instead of a session.
     */
    public LoginResult login(AuthRequestDTO dto, String clientIp, String userAgent) {
        loginAttempts.checkAllowed(clientIp, dto.getEmail());
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.getEmail(), dto.getPassword()));
        } catch (BadCredentialsException e) {
            if (loginAttempts.recordFailure(clientIp, dto.getEmail())) {
                userRepository.findByEmail(dto.getEmail()).ifPresent(user -> mail.sendLoginLockedAlert(
                        user.getEmail(), user.getName(), Instant.now(), DeviceName.of(userAgent)));
            }
            throw e;
        }
        loginAttempts.reset(clientIp, dto.getEmail());

        User user = userRepository.findByEmail(dto.getEmail()).orElseThrow();
        if (trustedDevices.isTrusted(dto.getDeviceToken(), user.getId())) {
            return new LoginResult(session(user, null), null);
        }
        Challenge challenge = challenges.start(user.getId());
        mail.sendSignInCode(user.getEmail(), user.getName(), challenge.code());
        return new LoginResult(null, new LoginChallengeDTO(challenge.id(), mask(user.getEmail())));
    }

    /** HU-33 · The right code opens the session and trusts this browser. It also proves the email (HU-31). */
    public AuthResponseDTO confirmLogin(String challengeId, String code) {
        User user = user(challenges.confirm(challengeId, code));
        markVerified(user);
        return session(user, trustedDevices.trust(user.getId()));
    }

    public LoginChallengeDTO resendLoginCode(String challengeId) {
        Challenge challenge = challenges.resend(challengeId);
        User user = user(challenge.userId());
        mail.sendSignInCode(user.getEmail(), user.getName(), challenge.code());
        return new LoginChallengeDTO(challenge.id(), mask(user.getEmail()));
    }

    /**
     * HU-31 · The link from the email confirms it and signs in on the browser that opened it,
     * which becomes trusted: whoever opens the link controls the inbox.
     */
    public AuthResponseDTO verifyEmail(String token) {
        User user = user(verifications.confirm(token));
        markVerified(user);
        return session(user, trustedDevices.trust(user.getId()));
    }

    /**
     * HU-31 · Another link, for the banner's "Reenviar". Answers with a fresh token either way, so one
     * that still says "unverified" catches up. It's the same session: the browser stays the open one.
     */
    public AuthResponseDTO resendVerification(Long userId) {
        User user = user(userId);
        if (!user.isEmailVerified()) {
            mail.sendVerificationLink(user.getEmail(), user.getName(), verifications.anotherLink(userId));
        }
        return session(user, null, sessions.current(userId));
    }

    /**
     * HU-09 · Emails a link to choose a new password. Answers the same whether the account exists or not,
     * so nobody learns who has one. Accounts created with Google (no password) can use it to set one.
     */
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> passwordResets.newLink(user.getId())
                .ifPresent(token -> mail.sendPasswordResetLink(user.getEmail(), user.getName(), token)));
    }

    /**
     * HU-09 · The link sets the new password and signs in on the browser that opened it, which becomes
     * trusted and the email verified: whoever opens the link controls the inbox. The new session closes
     * every other one, so whoever knew the old password is out at once.
     */
    public AuthResponseDTO resetPassword(String token, String password) {
        // Hash first: if it failed after confirm, the single-use link would be gone.
        String hash = passwordEncoder.encode(password);
        User user = user(passwordResets.confirm(token));
        user.setPassword(hash);
        user.setEmailVerified(true);
        userRepository.save(user);
        return session(user, trustedDevices.trust(user.getId()));
    }

    /**
     * HU-32 · Google already proved the email, so the account is verified and the browser trusted.
     * Found by Google's id first; else an account with the same email is joined (it keeps its password,
     * orders and wishlist); else a new one without password is created.
     */
    public AuthResponseDTO loginWithGoogle(String credential) {
        GoogleIdentity identity = google.verify(credential);
        User user = userRepository.findByGoogleSub(identity.subject())
                .or(() -> userRepository.findByEmail(identity.email()))
                .orElseGet(() -> newGoogleUser(identity));
        user.setGoogleSub(identity.subject());
        user.setEmailVerified(true);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two first sign-ins at once created the account twice; the other one won, use it.
            user = userRepository.findByGoogleSub(identity.subject()).orElseThrow(() -> e);
        }
        return session(user, trustedDevices.trust(user.getId()));
    }

    public String googleClientId() {
        return google.clientId();
    }

    private User newGoogleUser(GoogleIdentity identity) {
        User user = new User();
        String name = identity.name().strip();
        user.setName(name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name);
        user.setEmail(identity.email());
        user.setRole(Role.USER);
        return user;
    }

    private void markVerified(User user) {
        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            userRepository.save(user);
        }
    }

    private User user(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("La cuenta ya no existe."));
    }

    /** A new session, which closes the one open in any other browser. */
    private AuthResponseDTO session(User user, String deviceToken) {
        return session(user, deviceToken, sessions.start(user.getId()));
    }

    private AuthResponseDTO session(User user, String deviceToken, String sessionId) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtService.generateToken(
                userDetails, user.getId(), user.getName(), user.isEmailVerified(), sessionId);
        return new AuthResponseDTO(token, user.getEmail(), user.getRole().name(), user.isEmailVerified(), deviceToken);
    }

    /** "ana@mail.com" → "a***@mail.com": enough to recognize your inbox, not to learn someone's address. */
    static String mask(String email) {
        int at = email.indexOf('@');
        return email.charAt(0) + "***" + email.substring(at);
    }
}
