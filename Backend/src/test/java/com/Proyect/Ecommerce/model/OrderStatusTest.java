package com.Proyect.Ecommerce.model;

import static com.Proyect.Ecommerce.model.OrderStatus.CANCELADA;
import static com.Proyect.Ecommerce.model.OrderStatus.ENTREGADA;
import static com.Proyect.Ecommerce.model.OrderStatus.ENVIADA;
import static com.Proyect.Ecommerce.model.OrderStatus.PAGADA;
import static com.Proyect.Ecommerce.model.OrderStatus.PENDIENTE_PAGO;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** HU-18 · PENDIENTE_PAGO → PAGADA → ENVIADA → ENTREGADA, and PENDIENTE_PAGO|PAGADA → CANCELADA. Nothing else. */
class OrderStatusTest {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            PENDIENTE_PAGO, EnumSet.of(PAGADA, CANCELADA),
            PAGADA, EnumSet.of(ENVIADA, CANCELADA),
            ENVIADA, EnumSet.of(ENTREGADA),
            ENTREGADA, EnumSet.noneOf(OrderStatus.class),
            CANCELADA, EnumSet.noneOf(OrderStatus.class));

    @Test
    void onlyTheMovesOfTheStory() {
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                assertEquals(ALLOWED.get(from).contains(to), from.canTransitionTo(to), from + " → " + to);
            }
        }
    }
}
