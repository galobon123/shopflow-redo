package com.Proyect.Ecommerce.payment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** HU-18 · x-signature as Mercado Pago documents it. Expected values made with openssl dgst -sha256 -hmac. */
class WebhookSignatureTest {

    private static final String SECRET = "test-webhook-secret";
    private static final String REQUEST_ID = "bb56a2f1-6aae-46ac-982e-9dcd3581d08e";
    /** HMAC of "id:123456;request-id:bb56a2f1-6aae-46ac-982e-9dcd3581d08e;ts:1742505638683;". */
    private static final String V1 = "9912fe642bf0ea310759733dfef36b290480e0dd611e937db2e65052aca627f4";

    @Test
    void acceptsTheSignatureOfTheManifest() {
        assertTrue(WebhookSignature.isValid(SECRET, "ts=1742505638683,v1=" + V1, "123456", REQUEST_ID));
        assertTrue(WebhookSignature.isValid(SECRET, " ts=1742505638683 , v1=" + V1.toUpperCase(), "123456", REQUEST_ID));
    }

    /** A part missing from the notification is left out of the manifest; an alphanumeric id goes in lowercase. */
    @Test
    void leavesOutWhatTheNotificationDoesNotHave() {
        String v1 = "78a0bb582a18964ef76894176e7e26545a2face447c891be21b22278a82401de"; // "id:abc123;ts:1742505638683;"
        assertTrue(WebhookSignature.isValid(SECRET, "ts=1742505638683,v1=" + v1, "ABC123", null));
    }

    @Test
    void refusesAnythingElse() {
        assertFalse(WebhookSignature.isValid(SECRET, "ts=1742505638683,v1=" + V1, "999999", REQUEST_ID), "another payment");
        assertFalse(WebhookSignature.isValid(SECRET, "ts=1742505638684,v1=" + V1, "123456", REQUEST_ID), "another ts");
        assertFalse(WebhookSignature.isValid(SECRET, "ts=1742505638683,v1=" + V1, "123456", "other"), "another request");
        assertFalse(WebhookSignature.isValid("other-secret", "ts=1742505638683,v1=" + V1, "123456", REQUEST_ID));
        assertFalse(WebhookSignature.isValid(SECRET, "v1=" + V1, "123456", REQUEST_ID), "without ts");
        assertFalse(WebhookSignature.isValid(SECRET, "ts=1742505638683", "123456", REQUEST_ID), "without v1");
        assertFalse(WebhookSignature.isValid(SECRET, null, "123456", REQUEST_ID), "without the header");
        assertFalse(WebhookSignature.isValid(SECRET, "garbage", "123456", REQUEST_ID));
        assertFalse(WebhookSignature.isValid("", "ts=1742505638683,v1=" + V1, "123456", REQUEST_ID), "without a secret");
    }
}
