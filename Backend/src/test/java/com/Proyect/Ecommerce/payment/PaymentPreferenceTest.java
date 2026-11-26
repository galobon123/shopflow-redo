package com.Proyect.Ecommerce.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.Proyect.Ecommerce.model.Order;
import com.Proyect.Ecommerce.model.OrderItem;
import com.Proyect.Ecommerce.model.Product;

/**
 * HU-18 · auto_return only where Mercado Pago takes it: it refuses the preference (invalid_auto_return) when the
 * back_urls are on localhost, so in dev without a tunnel the buyer comes back with "Volver al sitio".
 */
class PaymentPreferenceTest {

    @Test
    void aPublicStoreGetsTheBuyerBackByItself() {
        assertEquals("approved", preferenceFrom("https://tienda.com.ar").autoReturn());
    }

    @Test
    void aStoreOnLocalhostSendsNoAutoReturn() {
        assertNull(preferenceFrom("http://localhost:4200").autoReturn());
        assertNull(preferenceFrom("http://127.0.0.1:4200/").autoReturn());
        assertEquals("http://localhost:4200/checkout/result/7/approved",
                preferenceFrom("http://localhost:4200").backUrls().success());
    }

    private static MercadoPagoClient.PreferenceRequest preferenceFrom(String storeUrl) {
        PaymentService payments = new PaymentService(new MercadoPagoClient("http://127.0.0.1:9", "TEST-token"),
                null, null, null, "secret", true, storeUrl, "");
        Product taza = new Product();
        taza.setId(15L);
        taza.setName("Taza");
        OrderItem item = new OrderItem();
        item.setProduct(taza);
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("1000.00"));
        Order order = new Order();
        order.setId(7L);
        order.setExpiresAt(LocalDateTime.of(2026, 11, 26, 18, 30));
        order.setItems(List.of(item));
        return payments.preferenceFor(order);
    }
}
