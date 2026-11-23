package com.Proyect.Ecommerce.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Proyect.Ecommerce.dto.cart.CartItemDTO;
import com.Proyect.Ecommerce.dto.cart.CartMergeDTO;
import com.Proyect.Ecommerce.exception.BadRequestException;
import com.Proyect.Ecommerce.service.CartService;
import com.Proyect.Ecommerce.service.CartService.CartOwner;

import jakarta.servlet.http.HttpServletRequest;

/**
 * HU-12 · Signed in, the cart is the account's and X-Cart-Id is ignored (it can't reach anyone's cart).
 * Without a session, the browser's X-Cart-Id names a guest cart; with neither, it's a 401.
 * A token that didn't authenticate (expired, invalid) is a 401 too, never a fall back to the guest cart:
 * the browser must hear that its session ended.
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    static final String CART_ID_HEADER = "X-Cart-Id";

    private final CartService cartService;
    /** Request-scoped proxy: the current request's headers. */
    private final HttpServletRequest request;

    public CartController(CartService cartService, HttpServletRequest request) {
        this.cartService = cartService;
        this.request = request;
    }

    @GetMapping
    public ResponseEntity<List<CartItemDTO>> getCart(Authentication auth,
                                                     @RequestHeader(name = CART_ID_HEADER, required = false) String cartId) {
        return ResponseEntity.ok(cartService.getCart(owner(auth, cartId)));
    }

    @PostMapping("/{productId}")
    public ResponseEntity<Void> addToCart(@PathVariable Long productId,
                                          @RequestParam(defaultValue = "1") int qty,
                                          Authentication auth,
                                          @RequestHeader(name = CART_ID_HEADER, required = false) String cartId) {
        cartService.addToCart(owner(auth, cartId), productId, qty);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{productId}")
    public ResponseEntity<Void> updateQty(@PathVariable Long productId,
                                          @RequestParam int qty,
                                          Authentication auth,
                                          @RequestHeader(name = CART_ID_HEADER, required = false) String cartId) {
        cartService.updateQty(owner(auth, cartId), productId, qty);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> removeFromCart(@PathVariable Long productId,
                                               Authentication auth,
                                               @RequestHeader(name = CART_ID_HEADER, required = false) String cartId) {
        cartService.removeFromCart(owner(auth, cartId), productId);
        return ResponseEntity.noContent().build(); // 204 No Content es la respuesta estándar en DELETE exitoso
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(Authentication auth,
                                          @RequestHeader(name = CART_ID_HEADER, required = false) String cartId) {
        cartService.clearCart(owner(auth, cartId));
        return ResponseEntity.noContent().build();
    }

    /** HU-12 · Right after signing in: the guest cart joins the account's. Safe to call twice or without one. */
    @PostMapping("/merge")
    public ResponseEntity<CartMergeDTO> merge(Authentication auth,
                                              @RequestHeader(name = CART_ID_HEADER, required = false) String cartId) {
        return ResponseEntity.ok(cartService.mergeGuestCart(extractUserId(auth), cartId));
    }

    // --- Helper Method ---

    private CartOwner owner(Authentication auth, String cartId) {
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return CartService.customer(extractUserId(auth));
        }
        if (request.getHeader("Authorization") != null) {
            throw new AuthenticationCredentialsNotFoundException("The token sent did not authenticate.");
        }
        if (cartId == null) {
            throw new AuthenticationCredentialsNotFoundException("Neither a session nor a guest cart id.");
        }
        return CartService.guest(cartId);
    }

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
