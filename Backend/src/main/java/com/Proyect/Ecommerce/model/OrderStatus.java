package com.Proyect.Ecommerce.model;

/** Order lifecycle (HU-18): PENDIENTE_PAGO → PAGADA → ENVIADA → ENTREGADA, or → CANCELADA before shipping. */
public enum OrderStatus {
    PENDIENTE_PAGO,
    PAGADA,
    ENVIADA,
    ENTREGADA,
    CANCELADA;

    /** HU-15 · Paid, shipped or delivered: a purchase that counts for recommendations and best sellers. */
    public boolean isPaid() {
        return this == PAGADA || this == ENVIADA || this == ENTREGADA;
    }

    /** HU-18 · The only moves an order can make; OrderService.transition refuses the rest with 409. */
    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PENDIENTE_PAGO -> next == PAGADA || next == CANCELADA;
            case PAGADA -> next == ENVIADA || next == CANCELADA;
            case ENVIADA -> next == ENTREGADA;
            case ENTREGADA, CANCELADA -> false;
        };
    }
}
