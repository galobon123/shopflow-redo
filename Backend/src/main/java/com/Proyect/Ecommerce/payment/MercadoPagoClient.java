package com.Proyect.Ecommerce.payment;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * HU-18 · The two calls ShopFlow makes to Mercado Pago's API: create a Checkout Pro preference and read a payment.
 * Plain HTTP with RestClient instead of the official SDK: that one brings its own HTTP client (Apache HttpClient 4,
 * end of life) and Gson, and keeps the access token in static state; this needs neither, its JSON goes through the
 * Jackson 3 Spring Boot 4 already has, and the base URL ({@code app.mercadopago.api-url}) points the tests at a
 * fake server. Short timeouts: a slow Mercado Pago fails the request instead of holding it.
 * <p>
 * Throws {@link org.springframework.web.client.RestClientException} when Mercado Pago fails or doesn't answer.
 */
@Component
public class MercadoPagoClient {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoClient.class);

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PreferenceRequest(
            List<Item> items,
            @JsonProperty("external_reference") String externalReference,
            @JsonProperty("back_urls") BackUrls backUrls,
            @JsonProperty("auto_return") String autoReturn,
            @JsonProperty("notification_url") String notificationUrl,
            @JsonProperty("binary_mode") boolean binaryMode,
            boolean expires,
            @JsonProperty("expiration_date_to") String expirationDateTo,
            @JsonProperty("payment_methods") PaymentMethods paymentMethods) {
    }

    public record Item(String id, String title, int quantity,
                       @JsonProperty("unit_price") BigDecimal unitPrice,
                       @JsonProperty("currency_id") String currencyId) {
    }

    public record BackUrls(String success, String pending, String failure) {
    }

    public record PaymentMethods(@JsonProperty("excluded_payment_types") List<Excluded> excludedPaymentTypes) {
    }

    public record Excluded(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Preference(String id,
                             @JsonProperty("init_point") String initPoint,
                             @JsonProperty("sandbox_init_point") String sandboxInitPoint) {
    }

    /** What ShopFlow reads of a payment; Mercado Pago sends much more. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payment(Long id,
                          String status,
                          @JsonProperty("external_reference") String externalReference,
                          @JsonProperty("transaction_amount") BigDecimal transactionAmount,
                          @JsonProperty("currency_id") String currencyId) {
    }

    private final RestClient http;
    private final boolean configured;

    public MercadoPagoClient(@Value("${app.mercadopago.api-url}") String apiUrl,
                             @Value("${app.mercadopago.access-token}") String accessToken) {
        this.configured = !accessToken.isBlank();
        if (!configured) {
            log.warn("MP_ACCESS_TOKEN is not set: paying with Mercado Pago answers 503 until it is");
        }
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requests.setReadTimeout(READ_TIMEOUT);
        this.http = RestClient.builder()
                .baseUrl(apiUrl)
                .requestFactory(requests)
                .defaultHeaders(headers -> headers.setBearerAuth(accessToken))
                .build();
    }

    /** False without an access token: the store works, only paying is off. */
    public boolean isConfigured() {
        return configured;
    }

    public Preference createPreference(PreferenceRequest preference) {
        return http.post().uri("/checkout/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .body(preference)
                .retrieve()
                .body(Preference.class);
    }

    public Payment payment(String paymentId) {
        return http.get().uri("/v1/payments/{id}", paymentId)
                .retrieve()
                .body(Payment.class);
    }
}
