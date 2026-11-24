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
}
