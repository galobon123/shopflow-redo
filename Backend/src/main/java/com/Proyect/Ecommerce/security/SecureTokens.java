package com.Proyect.Ecommerce.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Random secrets for links and devices, and the hash they are stored under.
 * Redis keeps only the hash, so whoever reads Redis can't use a link or pose as a device.
 */
public final class SecureTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecureTokens() {
    }

    /** 256 random bits, URL-safe (it travels in links). */
    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Six digits, zeros included ("004215"). */
    public static String newCode() {
        return "%06d".formatted(RANDOM.nextInt(1_000_000));
    }

    public static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Compares in constant time, so the response time doesn't tell how many digits were right. */
    public static boolean sameSecret(String expected, String given) {
        return given != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }
}
