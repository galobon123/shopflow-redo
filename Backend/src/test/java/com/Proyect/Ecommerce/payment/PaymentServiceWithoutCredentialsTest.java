package com.Proyect.Ecommerce.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.Proyect.Ecommerce.exception.ServiceUnavailableException;

/**
 * HU-18 · Without Mercado Pago's credentials the store still starts: paying and the webhook answer 503 (HU-03's
 * format, GlobalExceptionHandler) before touching anything, so nothing else is needed here.
 */
class PaymentServiceWithoutCredentialsTest {

    private final PaymentService payments = new PaymentService(
            new MercadoPagoClient("http://127.0.0.1:9", ""), null, null, null,
            "", true, "http://localhost:4200", "");

    @Test
    void payingIsNotAvailable() {
        ServiceUnavailableException error = assertThrows(ServiceUnavailableException.class,
                () -> payments.checkoutUrl(1L, 7L));
        assertEquals("El pago no está disponible en este momento. Probá de nuevo más tarde.", error.getMessage());
    }

    /** 5xx, so Mercado Pago sends the notice again once the secret is set. */
    @Test
    void theWebhookAsksMercadoPagoToTryLater() {
        assertThrows(ServiceUnavailableException.class,
                () -> payments.handle(new PaymentService.Notice("ts=1,v1=x", "r", "payment", "123", "1")));
    }
}
