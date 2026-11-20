package com.Proyect.Ecommerce.dto.auth;

/**
 * HU-33 · Right password from a new device: no session yet, a code went to {@code sentTo}
 * (masked, "a***@mail.com"). The browser sends the code back with {@code challengeId}.
 */
public record LoginChallengeDTO(String challengeId, String sentTo) {
}
