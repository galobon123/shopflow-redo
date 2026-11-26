package com.Proyect.Ecommerce.payment;

import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import com.Proyect.Ecommerce.exception.ConflictException;
import com.Proyect.Ecommerce.exception.InvalidSignatureException;
import com.Proyect.Ecommerce.exception.ServiceUnavailableException;
import com.Proyect.Ecommerce.model.Order;
import com.Proyect.Ecommerce.model.OrderStatus;
import com.Proyect.Ecommerce.payment.MercadoPagoClient.Payment;
import com.Proyect.Ecommerce.payment.MercadoPagoClient.Preference;
import com.Proyect.Ecommerce.payment.MercadoPagoClient.PreferenceRequest;
import com.Proyect.Ecommerce.repository.OrderRepository;
import com.Proyect.Ecommerce.service.OrderService;

/**
 * HU-18 · Paying an order with Mercado Pago (Checkout Pro), and the webhook that tells ShopFlow how it went.
 * The calls to Mercado Pago happen outside any database transaction; the order changes through OrderService.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    static final String CURRENCY = "ARS";
    /** A notice already handled is skipped for a day ({@code SET mp:event:{id} NX EX 86400}). */
    static final Duration EVENT_TTL = Duration.ofHours(24);
    static final String UNAVAILABLE = "El pago no está disponible en este momento. Probá de nuevo más tarde.";

    private static final ZoneId STORE_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final DateTimeFormatter MP_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    /** Cash and ATM payments are approved days later: by then the order was cancelled and its stock sold again. */
    private static final MercadoPagoClient.PaymentMethods ONLINE_ONLY = new MercadoPagoClient.PaymentMethods(
            List.of(new MercadoPagoClient.Excluded("ticket"), new MercadoPagoClient.Excluded("atm")));

    /** A Mercado Pago notification, as the webhook got it: headers, query string and body. */
    public record Notice(String signature, String requestId, String type, String dataId, String eventId) {
    }

    private final MercadoPagoClient mercadoPago;
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final StringRedisTemplate redis;
    private final String webhookSecret;
    private final boolean sandbox;
    private final String storeUrl;
    private final String apiUrl;

    public PaymentService(MercadoPagoClient mercadoPago,
                          OrderService orderService,
                          OrderRepository orderRepository,
                          StringRedisTemplate redis,
                          @Value("${app.mercadopago.webhook-secret}") String webhookSecret,
                          @Value("${app.mercadopago.sandbox}") boolean sandbox,
                          @Value("${app.public-url}") String storeUrl,
                          @Value("${app.api-public-url}") String apiUrl) {
        this.mercadoPago = mercadoPago;
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.redis = redis;
        this.webhookSecret = webhookSecret;
        this.sandbox = sandbox;
        this.storeUrl = withoutTrailingSlash(storeUrl);
        this.apiUrl = withoutTrailingSlash(apiUrl);
        if (webhookSecret.isBlank()) {
            log.warn("MP_WEBHOOK_SECRET is not set: Mercado Pago's notifications answer 503 until it is");
        }
    }

    /**
     * Where the customer pays the order. One preference per order, made the first time and reused on every attempt:
     * a rejected payment can be retried in the same checkout, the items and prices of an order never change, and
     * its {@code expiration_date_to} is the order's own. 503 without credentials or with Mercado Pago down.
     */
    public String checkoutUrl(Long userId, Long orderId) {
        if (!mercadoPago.isConfigured()) throw new ServiceUnavailableException(UNAVAILABLE);
        Order order = orderService.payable(userId, orderId);
        if (order.getMpCheckoutUrl() != null) return order.getMpCheckoutUrl();

        Preference preference;
        try {
            preference = mercadoPago.createPreference(preferenceFor(order));
        } catch (RestClientException e) {
            log.warn("Mercado Pago preference for order {} failed: {}", orderId, e.getMessage());
            throw new ServiceUnavailableException("No pudimos conectar con Mercado Pago. Probá de nuevo en unos minutos.");
        }
        String url = sandbox ? preference.sandboxInitPoint() : preference.initPoint();
        if (url == null) throw new ServiceUnavailableException(UNAVAILABLE);
        orderService.saveCheckoutUrl(orderId, url);
        return url;
    }

    /** Package-private for PaymentPreferenceTest. */
    PreferenceRequest preferenceFor(Order order) {
        Long id = order.getId();
        String result = storeUrl + "/checkout/result/" + id + "/";
        List<MercadoPagoClient.Item> items = order.getItems().stream()
                .map(item -> new MercadoPagoClient.Item(item.getProduct().getId().toString(), item.getProduct().getName(),
                        item.getQuantity(), item.getUnitPrice(), CURRENCY))
                .toList();
        // expires_at is the JVM's local time (UTC, see EcommerceApplication).
        String expiry = order.getExpiresAt() == null ? null
                : order.getExpiresAt().atZone(ZoneId.systemDefault()).withZoneSameInstant(STORE_ZONE).format(MP_DATE);
        return new PreferenceRequest(
                items,
                id.toString(),
                new MercadoPagoClient.BackUrls(result + "approved", result + "pending", result + "rejected"),
                // Mercado Pago refuses auto_return with back_urls on localhost (invalid_auto_return): in dev without a
                // tunnel the buyer comes back with "Volver al sitio" instead.
                isLocal(storeUrl) ? null : "approved",
                // Webhooks only (signed): without source_news Mercado Pago also sends the old unsigned IPN, which
                // would get 401 and be retried forever. It appends &data.id=…&type=payment; the signature is the same.
                apiUrl.isBlank() ? null : apiUrl + "/api/payments/webhook?source_news=webhooks",
                // Approved or rejected right away, never in_process for a manual review that can take days: by then
                // the order would be cancelled and its stock sold again.
                true,
                expiry != null,
                expiry,
                ONLINE_ONLY);
    }

    /**
     * The webhook. 401 with a bad signature; anything but a payment is ignored. Each notice is handled once
     * ({@code mp:event:{id}}): a repeat answers 200 without doing anything. If handling it fails (Mercado Pago or the
     * database down) its key is let go and the error answers 5xx, so Mercado Pago sends it again.
     */
    public void handle(Notice notice) {
        if (webhookSecret.isBlank()) throw new ServiceUnavailableException(UNAVAILABLE);
        if (!WebhookSignature.isValid(webhookSecret, notice.signature(), notice.dataId(), notice.requestId())) {
            throw new InvalidSignatureException();
        }
        if (!"payment".equals(notice.type()) || notice.dataId() == null || notice.dataId().isBlank()) return;

        String key = "mp:event:" + (notice.eventId() != null ? notice.eventId() : "payment:" + notice.dataId());
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", EVENT_TTL))) {
            log.info("Mercado Pago notice {} was already handled", key);
            return;
        }
        try {
            readPayment(notice.dataId()).ifPresent(this::apply);
        } catch (RuntimeException e) {
            try {
                redis.delete(key);
            } catch (RuntimeException ignored) {
                // Redis is down too: the key expires on its own and the notice is handled then.
            }
            throw e;
        }
    }

    /**
     * Never the body of the notice: what Mercado Pago's API says about the payment. Only a 404 (no such payment, or
     * one of another account, like the panel's "Simular notificación") won't change on a retry: empty, and the notice
     * answers 200 keeping its key. Any other 4xx (401/403: a wrong, expired or sandbox-vs-production token; 429) is
     * ours to fix, and the notice may be a real approved payment: ERROR, and 503 so Mercado Pago sends it again.
     * So does Mercado Pago down or slow (5xx, timeout).
     */
    private Optional<Payment> readPayment(String paymentId) {
        try {
            return Optional.ofNullable(mercadoPago.payment(paymentId));
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Mercado Pago has no payment {}: notice ignored", paymentId);
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            log.error("Mercado Pago answered {} for payment {}: check MP_ACCESS_TOKEN; Mercado Pago will notify again",
                    e.getStatusCode().value(), paymentId);
            throw new ServiceUnavailableException("No pudimos consultar el pago en Mercado Pago.");
        } catch (RestClientException e) {
            log.warn("Mercado Pago payment {} could not be read, Mercado Pago will notify again: {}", paymentId, e.getMessage());
            throw new ServiceUnavailableException("No pudimos consultar el pago en Mercado Pago.");
        }
    }

    private void apply(Payment payment) {
        Optional<Order> found = orderOf(payment);
        if (found.isEmpty()) {
            log.warn("Mercado Pago payment {} names no order of ShopFlow (external_reference {})",
                    payment.id(), payment.externalReference());
            return;
        }
        Order order = found.get();
        switch (payment.status() == null ? "" : payment.status()) {
            case "approved" -> approved(order, payment);
            case "refunded", "charged_back" -> reversed(order, payment);
            // The order keeps waiting: the customer can try again until it expires.
            case "rejected", "cancelled" -> log.info("Mercado Pago payment {} of order {} was {}",
                    payment.id(), order.getId(), payment.status());
            default -> { } // pending, in_process, authorized: the next notice says how it ended
        }
    }

    /**
     * Pays the order. An approved payment never revives a cancelled order, nor pays one twice: those are marked for
     * review (paymentIssue) with an ERROR log, for someone to give the money back or ship by hand.
     */
    private void approved(Order order, Payment payment) {
        String paymentId = String.valueOf(payment.id());
        if (payment.transactionAmount() == null || payment.transactionAmount().compareTo(order.getTotal()) != 0
                || !CURRENCY.equals(payment.currencyId())) {
            review(order, "Pago " + paymentId + " aprobado por " + payment.transactionAmount() + " "
                    + payment.currencyId() + ", pero el pedido es de " + order.getTotal() + " " + CURRENCY + ".");
            return;
        }
        if (order.getStatus() == OrderStatus.PENDIENTE_PAGO) {
            try {
                orderService.markPaid(order.getId(), paymentId);
                return;
            } catch (ConflictException e) {
                // The expiry job cancelled it a moment ago.
                order = orderRepository.findById(order.getId()).orElseThrow();
            }
        }
        if (order.getStatus().isPaid() && paymentId.equals(order.getMpPaymentId())) return;
        review(order, order.getStatus() == OrderStatus.CANCELADA
                ? "Pago " + paymentId + " aprobado con el pedido ya cancelado: hay que devolverlo."
                : "Pago " + paymentId + " aprobado con el pedido ya pagado por el pago " + order.getMpPaymentId() + ".");
    }

    /** A refund or chargeback of the payment that paid it cancels the order and gives its stock back. */
    private void reversed(Order order, Payment payment) {
        String paymentId = String.valueOf(payment.id());
        if (!paymentId.equals(order.getMpPaymentId())) {
            log.info("Mercado Pago payment {} was {}, but it isn't the one that paid order {}",
                    paymentId, payment.status(), order.getId());
            return;
        }
        switch (order.getStatus()) {
            case PAGADA -> orderService.transition(order.getId(), OrderStatus.CANCELADA);
            case CANCELADA -> { }
            default -> review(order, "Pago " + paymentId + " " + payment.status() + " con el pedido "
                    + order.getStatus() + ".");
        }
    }

    private void review(Order order, String issue) {
        log.error("Order {} needs review: {}", order.getId(), issue);
        orderService.flagPayment(order.getId(), issue);
    }

    private Optional<Order> orderOf(Payment payment) {
        try {
            return orderRepository.findById(Long.valueOf(payment.externalReference()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static boolean isLocal(String url) {
        String host = URI.create(url).getHost();
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
    }

    private static String withoutTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
