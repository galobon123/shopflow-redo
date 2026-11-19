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
        String key = buildCartKey(userId);
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

        if (productIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<CartItemDTO> result = new ArrayList<>();
        List<String> orphanProductIds = new ArrayList<>();

        for (Map.Entry<Object, Object> entry : rawCart.entrySet()) {
            try {
                Long productId = Long.parseLong(entry.getKey().toString());
                Product product = productMap.get(productId);

                if (product == null || !product.isActive()) {
                    orphanProductIds.add(productId.toString());
                    continue;
                }

                int quantity = Integer.parseInt(entry.getValue().toString());

                int currentStock = product.getStock();
                int effectiveQuantity = Math.min(quantity, currentStock);

                if (effectiveQuantity <= 0) {
                    orphanProductIds.add(productId.toString());
                    continue;
                }

                CartItemDTO dto = new CartItemDTO();
                dto.setProductId(productId);
                dto.setProductName(product.getName());
                dto.setProductPrice(product.getPrice());
                dto.setProductImageUrl(product.getImageUrl());
                dto.setQuantity(effectiveQuantity);

                result.add(dto);

            } catch (NumberFormatException ignored) {
                orphanProductIds.add(entry.getKey().toString());
            }
        }

        if (!orphanProductIds.isEmpty()) {
            redisTemplate.opsForHash().delete(key, orphanProductIds.toArray());
        }

        return result;
    }

    public void addToCart(Long userId, Long productId, int qty) {
        if (qty <= 0) {
            throw new BadRequestException("La cantidad debe ser mayor a cero.");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("El producto con ID " + productId + " no existe."));

        if (!product.isActive()) {
            throw new ConflictException("El producto no se encuentra disponible.");
        }

        String key = buildCartKey(userId);
        Object currentQtyObj = redisTemplate.opsForHash().get(key, productId.toString());
        int currentQtyInCart = currentQtyObj != null ? Integer.parseInt(currentQtyObj.toString()) : 0;
        int targetQty = currentQtyInCart + qty;

        if (targetQty > product.getStock()) {
            throw new BadRequestException("No hay stock suficiente: quedan "
            + product.getStock() + " y en total pediste " + targetQty + ".");
        }

        redisTemplate.opsForHash().put(key, productId.toString(), String.valueOf(targetQty));
        refreshExpiry(key);
    }

    public void updateQty(Long userId, Long productId, int qty) {
        String key = buildCartKey(userId);

        if (qty <= 0) {
            removeFromCart(userId, productId);
            return;
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("El producto con ID " + productId + " no existe."));

        if (qty > product.getStock()) {
            throw new BadRequestException("No hay stock suficiente: quedan " + product.getStock() + ".");
        }

        redisTemplate.opsForHash().put(key, productId.toString(), String.valueOf(qty));
        refreshExpiry(key);
    }

    public void removeFromCart(Long userId, Long productId) {
        String key = buildCartKey(userId);
        redisTemplate.opsForHash().delete(key, productId.toString());

        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            refreshExpiry(key);
        }
    }

    public void clearCart(Long userId) {
        redisTemplate.delete(buildCartKey(userId));
    }

    //#region Helper Methods

    private String buildCartKey(Long userId) {
        return CART_KEY_PREFIX + userId;
    }

    private void refreshExpiry(String key) {
        redisTemplate.expire(key, CART_TTL_DAYS, TimeUnit.DAYS);
    }

    //#endregion
}