package com.Proyect.Ecommerce.model;

import java.util.Locale;

/** HU-06 · One spelling per address: " Ana@Mail.com " and "ana@mail.com" are the same account. */
public final class Emails {

    private Emails() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
