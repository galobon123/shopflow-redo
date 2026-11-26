package com.Proyect.Ecommerce.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import com.Proyect.Ecommerce.dto.order.OrderRequestDTO;
import com.Proyect.Ecommerce.dto.order.OrderResponseDTO;
import com.Proyect.Ecommerce.exception.BadRequestException;
import com.Proyect.Ecommerce.exception.PurchaseInProgressException;

/**
 * HU-17 · POST /api/orders around {@link OrderService#createOrder}, with two Redis keys taken before its
 * transaction and settled after it committed:
 * <ul>
 * <li>{@code order:idem:{userId}:{key}} makes the purchase idempotent. It holds {@code processing:…} while the
 * order is being created (a short TTL, so a crash can't leave it stuck) and {@code done:{orderId}} for a day.
 * A repeat gets that same order without taking stock again; one that arrives while the first still runs gets 409.
 * A failed attempt (no stock, database error…) frees its key, so the same purchase can be retried once the cart
 * is fixed. A key names the purchase, not the cart: used again with another cart it still answers its order.</li>
 * <li>{@code order:lock:{userId}} lets one checkout run at a time per customer, so two tabs (two keys) can't buy
 * the same cart twice.</li>
 * </ul>
 * Once the order is committed, what's written to Redis afterwards is best effort: a Redis failure there is logged
 * and the customer still gets the order (the lock and the "processing" state expire on their own).
 */
@Service
public class OrderCheckout {

    private static final Logger log = LoggerFactory.getLogger(OrderCheckout.class);

    static final Duration PROCESSING_TTL = Duration.ofSeconds(60);
    static final Duration DONE_TTL = Duration.ofHours(24);
    static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private static final String PROCESSING = "processing:";
    private static final String DONE = "done:";

    /** Deletes the key only if it still holds the caller's value: after its TTL, it may belong to someone else. */
    private static final RedisScript<Long> DELETE_IF_MINE = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end return 0", Long.class);

    /** The order, and whether this request created it (201) or found it already made (200). */
    public record Placed(OrderResponseDTO order, boolean created) {
    }

    private final OrderService orderService;
    private final StringRedisTemplate redis;

    public OrderCheckout(OrderService orderService, StringRedisTemplate redis) {
        this.orderService = orderService;
        this.redis = redis;
    }

    public Placed place(Long userId, String idempotencyKey, OrderRequestDTO dto) {
        String idemKey = "order:idem:" + userId + ":" + parse(idempotencyKey);
        String token = UUID.randomUUID().toString();
        String processing = PROCESSING + token;

        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(idemKey, processing, PROCESSING_TTL))) {
            return new Placed(previous(userId, idemKey), false);
        }

        OrderResponseDTO order;
        try {
            order = createAlone(userId, token, dto);
        } catch (RuntimeException e) {
            releaseQuietly(idemKey, processing);
            throw e;
        }
        try {
            redis.opsForValue().set(idemKey, DONE + order.getId(), DONE_TTL);
        } catch (RuntimeException e) {
            log.warn("Order {} created but its Idempotency-Key could not be marked done", order.getId(), e);
        }
        return new Placed(order, true);
    }

    /** Only while holding the customer's checkout lock, let go when done whatever happens. */
    private OrderResponseDTO createAlone(Long userId, String token, OrderRequestDTO dto) {
        String lockKey = "order:lock:" + userId;
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, token, LOCK_TTL))) {
            throw new PurchaseInProgressException();
        }
        try {
            return orderService.createOrder(userId, dto);
        } finally {
            releaseQuietly(lockKey, token);
        }
    }

    /** The order a used key made, or 409 while it's still being made (or it failed a moment ago: retry). */
    private OrderResponseDTO previous(Long userId, String idemKey) {
        String value = redis.opsForValue().get(idemKey);
        if (value == null || !value.startsWith(DONE)) {
            throw new PurchaseInProgressException();
        }
        return orderService.findOwn(userId, Long.valueOf(value.substring(DONE.length())));
    }

    /**
     * Deletes the key only if it still holds the caller's value. A Redis failure here must not hide what happened
     * (the order made, or why it failed): the key expires on its own.
     */
    private void releaseQuietly(String key, String value) {
        try {
            redis.execute(DELETE_IF_MINE, List.of(key), value);
        } catch (RuntimeException e) {
            log.warn("Could not release {}; it expires on its own", key, e);
        }
    }

    private static UUID parse(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Falta el encabezado Idempotency-Key.");
        }
        try {
            return UUID.fromString(idempotencyKey.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("El encabezado Idempotency-Key tiene que ser un UUID.");
        }
    }
}
