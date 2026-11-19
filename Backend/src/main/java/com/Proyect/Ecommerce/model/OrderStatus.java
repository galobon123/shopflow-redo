package com.Proyect.Ecommerce.model;

/** Order lifecycle (HU-18): PENDIENTE_PAGO → PAGADA → ENVIADA → ENTREGADA, or → CANCELADA before shipping. */
public enum OrderStatus {
    PENDIENTE_PAGO,
    PAGADA,
    ENVIADA,
    ENTREGADA,
    CANCELADA
}
