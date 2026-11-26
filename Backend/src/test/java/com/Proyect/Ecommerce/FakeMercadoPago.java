package com.Proyect.Ecommerce;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * HU-18 · Mercado Pago's API for the integration tests: a real HTTP server on a free port (the app's
 * {@code app.mercadopago.api-url} points at it), so the tests never need the network. It answers the two calls the
 * store makes, creating a preference and reading a payment, and keeps every request it got.
 */
public class FakeMercadoPago {

    public record Request(String method, String path, String authorization, String body) {
    }

    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final Map<String, String> payments = new ConcurrentHashMap<>();
    private final AtomicInteger preferences = new AtomicInteger();
    /** The status every call answers instead of its result; 0: none. */
    private volatile int failure;

    public FakeMercadoPago() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/checkout/preferences", this::preference);
        server.createContext("/v1/payments/", this::payment);
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void stop() {
        server.stop(0);
    }

    /** What {@code GET /v1/payments/{id}} answers from now on. */
    public void payment(long id, String status, Long orderId, String amount, String currency) {
        payments.put(String.valueOf(id), """
                {"id": %d, "status": "%s", "status_detail": "x", "external_reference": %s,
                 "transaction_amount": %s, "currency_id": "%s", "payer": {"email": "test@example.com"}}
                """.formatted(id, status, orderId == null ? "null" : "\"" + orderId + "\"", amount, currency));
    }

    /** Every call answers 500, like a Mercado Pago that's down. */
    public void goDown() {
        failure = 500;
    }

    /** Every call answers {@code status}: 401 is what a wrong or expired access token gets. */
    public void refuseWith(int status) {
        failure = status;
    }

    public void comeBack() {
        failure = 0;
    }

    public void clear() {
        requests.clear();
        payments.clear();
        preferences.set(0);
        failure = 0;
    }

    public List<Request> requests(String method, String pathPrefix) {
        return requests.stream().filter(r -> r.method().equals(method) && r.path().startsWith(pathPrefix)).toList();
    }

    public static String sandboxUrl(int preference) {
        return "https://sandbox.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-" + preference;
    }

    private void preference(HttpExchange exchange) throws IOException {
        record(exchange);
        if (failure != 0) {
            answer(exchange, failure, "{\"message\": \"refused\"}");
            return;
        }
        int n = preferences.incrementAndGet();
        answer(exchange, 201, """
                {"id": "pref-%d", "init_point": "https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-%d",
                 "sandbox_init_point": "%s", "collector_id": 1}
                """.formatted(n, n, sandboxUrl(n)));
    }

    private void payment(HttpExchange exchange) throws IOException {
        record(exchange);
        if (failure != 0) {
            answer(exchange, failure, "{\"message\": \"refused\"}");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String payment = payments.get(path.substring(path.lastIndexOf('/') + 1));
        if (payment == null) {
            answer(exchange, 404, "{\"message\": \"Payment not found\"}");
        } else {
            answer(exchange, 200, payment);
        }
    }

    private void record(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Authorization"), body));
    }

    private static void answer(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
