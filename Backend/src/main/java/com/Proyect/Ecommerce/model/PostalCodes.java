package com.Proyect.Ecommerce.model;

import java.util.Locale;

/**
 * HU-16 · Argentine postal codes: the CPA ("X5152ABC") or the old 4-digit code ("5152").
 * One spelling: " x5152 abc " is saved as "X5152ABC".
 */
public final class PostalCodes {

    public static final String PATTERN = "^([A-Z]\\d{4}[A-Z]{3}|\\d{4})$";
    public static final String INVALID = "Código postal inválido";

    private PostalCodes() {
    }

    public static String normalize(String code) {
        return code == null ? null : code.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
    }
}
