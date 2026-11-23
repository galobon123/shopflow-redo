package com.Proyect.Ecommerce.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.Proyect.Ecommerce.dto.cart.CartItemDTO;
import com.Proyect.Ecommerce.exception.BadRequestException;
import com.Proyect.Ecommerce.exception.ConflictException;
import com.Proyect.Ecommerce.exception.NotFoundException;
import com.Proyect.Ecommerce.model.Product;
import com.Proyect.Ecommerce.repository.ProductRepository;

/**
 * HU-13 · The only class that reads or writes carts in Redis (hash productId → qty).
 * The public methods name the cart by user; the work is done on a key, so a guest cart
 * (HU-12, under the same prefix) can reuse it.
 */
@Service
public class CartService {

    private static final String CART_KEY_PREFIX = "cart:";
    private static final long CART_TTL_DAYS = 3;

    private final StringRedisTemplate redisTemplate;
    private final ProductRepository productRepository;

    public CartService(StringRedisTemplate redisTemplate,
                       ProductRepository productRepository) {
        this.redisTemplate = redisTemplate;
        this.productRepository = productRepository;
    }

    public List<CartItemDTO> getCart(Long userId) {
        return readCart(userKey(userId));
    }

    public void addToCart(Long userId, Long productId, int qty) {
        if (qty <= 0) {
            throw new BadRequestException("La cantidad debe ser mayor a cero.");
        }
        String key = userKey(userId);
        Object currentQtyObj = redisTemplate.opsForHash().get(key, productId.toString());
        int currentQtyInCart = currentQtyObj != null ? Integer.parseInt(currentQtyObj.toString()) : 0;
        writeQty(key, productId, currentQtyInCart + qty);
    }

    public void updateQty(Long userId, Long productId, int qty) {
        String key = userKey(userId);
        if (qty <= 0) {
            removeItem(key, productId);
            return;
        }
        writeQty(key, productId, qty);
    }

    public void removeFromCart(Long userId, Long productId) {
        removeItem(userKey(userId), productId);
    }

    public void clearCart(Long userId) {
        redisTemplate.delete(userKey(userId));
    }

    //#region Helper Methods

    private String userKey(Long userId) {
        return CART_KEY_PREFIX + userId;
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
