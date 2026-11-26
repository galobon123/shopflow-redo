package com.Proyect.Ecommerce.payment;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HU-18 · Checks Mercado Pago's {@code x-signature} header ({@code ts=…,v1=…}): v1 is the HMAC-SHA256, with the
 * webhook secret, of the manifest {@code id:{data.id};request-id:{x-request-id};ts:{ts};}, where a value missing
 * from the notification leaves its part out (Mercado Pago's rule). Compared in constant time.
 * <p>
 * The age of {@code ts} is not checked: Mercado Pago's retries can come hours later, and replaying a real
 * notification only makes the webhook read the payment from the API again, which changes nothing.
 */
final class WebhookSignature {

    private WebhookSignature() {
    }

    static boolean isValid(String secret, String header, String dataId, String requestId) {
        if (header == null || secret.isBlank()) return false;
        String ts = null;
        String v1 = null;
        for (String part : header.split(",")) {
            String[] pair = part.split("=", 2);
            if (pair.length != 2) continue;
            switch (pair[0].trim()) {
                case "ts" -> ts = pair[1].trim();
                case "v1" -> v1 = pair[1].trim();
                default -> { }
            }
        }
        if (ts == null || v1 == null) return false;
        String expected = sign(secret, manifest(dataId, requestId, ts));
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                v1.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    /** An alphanumeric data.id goes in lowercase, as Mercado Pago signs it. */
    static String manifest(String dataId, String requestId, String ts) {
        StringBuilder manifest = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) manifest.append("id:").append(dataId.toLowerCase(Locale.ROOT)).append(';');
        if (requestId != null && !requestId.isBlank()) manifest.append("request-id:").append(requestId).append(';');
        return manifest.append("ts:").append(ts).append(';').toString();
    }

    static String sign(String secret, String manifest) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }
}
