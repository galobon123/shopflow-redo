package com.Proyect.Ecommerce.exception;

/**
 * HU-17 · 409 for a purchase that is still being created (the same attempt, or another tab of the same customer).
 * Its {@code reason} lets the browser wait on the checkout instead of going back to the cart.
 */
public class PurchaseInProgressException extends ConflictException {
    public PurchaseInProgressException() {
        super("Tu compra se está procesando.");
    }
}
