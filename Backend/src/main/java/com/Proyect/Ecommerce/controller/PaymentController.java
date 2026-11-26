package com.Proyect.Ecommerce.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Proyect.Ecommerce.dto.order.PaymentCheckoutDTO;
import com.Proyect.Ecommerce.payment.PaymentService;

/** HU-18 · Paying an order with Mercado Pago, and Mercado Pago telling the store how the payment went. */
@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Where to send the customer to pay one of their orders. 404 if it isn't theirs, 409 if it's no longer waiting
     * for its payment or its time is up, 503 if paying is off (no credentials) or Mercado Pago is down.
     */
    @PostMapping("/api/orders/{id}/payment")
    public ResponseEntity<PaymentCheckoutDTO> pay(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getCredentials();
        return ResponseEntity.ok(new PaymentCheckoutDTO(paymentService.checkoutUrl(userId, id)));
    }

    /**
     * Mercado Pago's webhook, public (it signs it instead). Mercado Pago sends {@code ?data.id=…&type=payment} and a
     * body with the same data plus the notice's own id; the query string is what the signature covers.
     */
    @PostMapping("/api/payments/webhook")
    public ResponseEntity<Void> webhook(@RequestHeader(name = "x-signature", required = false) String signature,
                                        @RequestHeader(name = "x-request-id", required = false) String requestId,
                                        @RequestParam(name = "data.id", required = false) String dataId,
                                        @RequestParam(name = "type", required = false) String type,
                                        @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> notice = body == null ? Map.of() : body;
        Object data = notice.get("data");
        if (dataId == null && data instanceof Map<?, ?> fields && fields.get("id") != null) {
            dataId = String.valueOf(fields.get("id"));
        }
        if (type == null && notice.get("type") != null) type = String.valueOf(notice.get("type"));
        String eventId = notice.get("id") == null ? null : String.valueOf(notice.get("id"));

        paymentService.handle(new PaymentService.Notice(signature, requestId, type, dataId, eventId));
        return ResponseEntity.ok().build();
    }
}
