package com.Proyect.Ecommerce.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Proyect.Ecommerce.dto.cart.CartItemDTO;
import com.Proyect.Ecommerce.exception.BadRequestException;
import com.Proyect.Ecommerce.service.CartService;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ResponseEntity<List<CartItemDTO>> getCart(Authentication auth) {
        Long userId = extractUserId(auth);
        return ResponseEntity.ok(cartService.getCart(userId));
    }

    @PostMapping("/{productId}")
    public ResponseEntity<Void> addToCart(@PathVariable Long productId,
                                          @RequestParam(defaultValue = "1") int qty,
                                          Authentication auth) {
        Long userId = extractUserId(auth);
        cartService.addToCart(userId, productId, qty);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{productId}")
    public ResponseEntity<Void> updateQty(@PathVariable Long productId,
                                          @RequestParam int qty,
                                          Authentication auth) {
        Long userId = extractUserId(auth);
        cartService.updateQty(userId, productId, qty);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> removeFromCart(@PathVariable Long productId,
                                               Authentication auth) {
        Long userId = extractUserId(auth);
        cartService.removeFromCart(userId, productId);
        return ResponseEntity.noContent().build(); // 204 No Content es la respuesta estándar en DELETE exitoso
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(Authentication auth) {
        Long userId = extractUserId(auth);
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }

    // --- Helper Method ---

    private Long extractUserId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new BadRequestException("Usuario no autenticado");
        }

        if (auth.getCredentials() instanceof Long userId) {
            return userId;
        }

        try {
            return Long.parseLong(auth.getName());
        } catch (NumberFormatException e) {
            throw new BadRequestException("No se pudo extraer un userId válido de la autenticación.");
        }
    }
}