package com.Proyect.Ecommerce.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import com.Proyect.Ecommerce.dto.cart.CartItemDTO;
import com.Proyect.Ecommerce.dto.cart.CartMergeDTO;
import com.Proyect.Ecommerce.exception.BadRequestException;
import com.Proyect.Ecommerce.exception.ConflictException;
import com.Proyect.Ecommerce.exception.NotFoundException;
import com.Proyect.Ecommerce.model.Product;
import com.Proyect.Ecommerce.repository.ProductRepository;

/**
 * HU-13 · The only class that reads or writes carts in Redis (hash productId → qty).
 * HU-12 · A cart belongs to a customer (cart:{userId}) or to a guest (cart:guest:{uuid});
 * callers name it with {@link #customer} or {@link #guest}, and the key format stays here.
 */
@Service
public class CartService {

    private static final String CART_KEY_PREFIX = "cart:";
    private static final String GUEST_KEY_PREFIX = CART_KEY_PREFIX + "guest:";
    private static final long CART_TTL_DAYS = 3;

    /** HU-12 · Reads and deletes a hash in one step, so two merges can't both take the same guest cart. */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static final RedisScript<List<String>> TAKE_HASH = (RedisScript) new DefaultRedisScript<>(
            "local entries = redis.call('HGETALL', KEYS[1]) redis.call('DEL', KEYS[1]) return entries", List.class);

    /** Which cart a call is about. Only built by {@link #customer} and {@link #guest}. */
    public static final class CartOwner {
        private final String key;

        private CartOwner(String key) {
            this.key = key;
        }
    }

    public static CartOwner customer(Long userId) {
        return new CartOwner(CART_KEY_PREFIX + userId);
    }

    /** HU-12 · The X-Cart-Id the browser made up; only a UUID is accepted, so it can't point at another key. */
    public static CartOwner guest(String cartId) {
        try {
            return new CartOwner(GUEST_KEY_PREFIX + UUID.fromString(cartId));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException("El identificador del carrito no es válido.");
        }
    }

    private final StringRedisTemplate redisTemplate;
    private final ProductRepository productRepository;

    public CartService(StringRedisTemplate redisTemplate,
                       ProductRepository productRepository) {
        this.redisTemplate = redisTemplate;
        this.productRepository = productRepository;
    }

    public List<CartItemDTO> getCart(CartOwner owner) {
        return readCart(owner.key);
    }

    public void addToCart(CartOwner owner, Long productId, int qty) {
        if (qty <= 0) {
            throw new BadRequestException("La cantidad debe ser mayor a cero.");
        }
        String key = owner.key;
        Object currentQtyObj = redisTemplate.opsForHash().get(key, productId.toString());
        int currentQtyInCart = currentQtyObj != null ? Integer.parseInt(currentQtyObj.toString()) : 0;
        writeQty(key, productId, currentQtyInCart + qty);
    }

    public void updateQty(CartOwner owner, Long productId, int qty) {
        String key = owner.key;
        if (qty <= 0) {
            removeItem(key, productId);
            return;
        }
        writeQty(key, productId, qty);
    }

    public void removeFromCart(CartOwner owner, Long productId) {
        removeItem(owner.key, productId);
    }

    public void clearCart(CartOwner owner) {
        redisTemplate.delete(owner.key);
    }

    /**
     * HU-12 · On signing in, the guest cart is added to the account's (HINCRBY) and deleted.
     * Here, unlike in the rest of the cart, a sum above the stock is trimmed to it, with a notice per product.
     * Products the guest had that are no longer available come along as they are: getCart shows them
     * as "No disponible" (HU-13), so nothing the customer chose disappears without a word.
     * Calling it again, or without a guest cart, just returns the account's cart.
     */
    public CartMergeDTO mergeGuestCart(Long userId, String guestCartId) {
        String key = customer(userId).key;
        List<String> notices = new ArrayList<>();

        if (guestCartId != null) {
            List<String> guestEntries = redisTemplate.execute(TAKE_HASH, List.of(guest(guestCartId).key));
            if (guestEntries != null && !guestEntries.isEmpty()) {
                notices = addEntries(key, guestEntries);
            }
        }

        return new CartMergeDTO(readCart(key), notices);
    }

    //#region Helper Methods

    /** `entries` comes flat from HGETALL: productId, qty, productId, qty… */
    private List<String> addEntries(String key, List<String> entries) {
        Map<Long, Integer> lines = new LinkedHashMap<>();
        for (int i = 0; i + 1 < entries.size(); i += 2) {
            try {
                int qty = Integer.parseInt(entries.get(i + 1));
                if (qty > 0) {
                    lines.merge(Long.parseLong(entries.get(i)), qty, Integer::sum);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        Map<Long, Product> productMap = productRepository.findAllById(lines.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<String> notices = new ArrayList<>();
        lines.forEach((productId, qty) -> {
            Product product = productMap.get(productId);
            if (product == null) {
                return;
            }
            String field = productId.toString();
            long total = redisTemplate.opsForHash().increment(key, field, qty);
            boolean available = product.isActive() && product.getStock() > 0;
            if (available && total > product.getStock()) {
                redisTemplate.opsForHash().put(key, field, String.valueOf(product.getStock()));
                notices.add("Ajustamos la cantidad de " + product.getName() + " al stock disponible");
            }
        });
        refreshExpiry(key);
        return notices;
    }

    /**
     * HU-13 · Deactivated or sold-out products stay in the cart as available=false, and the
     * quantity is never trimmed to the stock: the customer sees it and decides.
     * Only entries that can't be shown (unreadable, or a product row that no longer exists) are dropped.
     */
    private List<CartItemDTO> readCart(String key) {
        Map<Object, Object> rawCart = redisTemplate.opsForHash().entries(key);

        if (rawCart.isEmpty()) {
            return List.of();
        }

        refreshExpiry(key);

        Set<Long> productIds = new HashSet<>();
        for (Object k : rawCart.keySet()) {
            try {
                productIds.add(Long.parseLong(k.toString()));
            } catch (NumberFormatException ignored) {
            }
        }

        Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<CartItemDTO> result = new ArrayList<>();
        List<String> unreadable = new ArrayList<>();

        for (Map.Entry<Object, Object> entry : rawCart.entrySet()) {
            try {
                Long productId = Long.parseLong(entry.getKey().toString());
                Product product = productMap.get(productId);
                int quantity = Integer.parseInt(entry.getValue().toString());

                if (product == null || quantity <= 0) {
                    unreadable.add(entry.getKey().toString());
                    continue;
                }

                CartItemDTO dto = new CartItemDTO();
                dto.setProductId(productId);
                dto.setProductName(product.getName());
                dto.setProductPrice(product.getPrice());
                dto.setProductImageUrl(product.getImageUrl());
                dto.setQuantity(quantity);
                dto.setStock(product.getStock());
                dto.setAvailable(product.isActive() && product.getStock() > 0);

                result.add(dto);

            } catch (NumberFormatException ignored) {
                unreadable.add(entry.getKey().toString());
            }
        }

        if (!unreadable.isEmpty()) {
            redisTemplate.opsForHash().delete(key, unreadable.toArray());
        }

        return result;
    }

    /** HU-13 · The one stock rule: more than the stock is a 409 that says how many are left. */
    private void writeQty(String key, Long productId, int qty) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("El producto con ID " + productId + " no existe."));

        if (!product.isActive()) {
            throw new ConflictException("El producto no se encuentra disponible.");
        }

        if (qty > product.getStock()) {
            throw new ConflictException(product.getStock() > 0
                    ? "Solo quedan " + product.getStock() + "."
                    : "El producto se quedó sin stock.");
        }

        redisTemplate.opsForHash().put(key, productId.toString(), String.valueOf(qty));
        refreshExpiry(key);
    }

    private void removeItem(String key, Long productId) {
        redisTemplate.opsForHash().delete(key, productId.toString());

        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            refreshExpiry(key);
        }
    }

    private void refreshExpiry(String key) {
        redisTemplate.expire(key, CART_TTL_DAYS, TimeUnit.DAYS);
    }

    //#endregion
}
