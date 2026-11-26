package com.Proyect.Ecommerce.service;

import com.Proyect.Ecommerce.model.OrderStatus;

/**
 * HU-15 · An order changed status (paid now; HU-18 will also cancel paid ones). {@link GraphSync} doesn't look at
 * which: it gives the order PURCHASED relations in Neo4j while Postgres has it paid or later, and takes them away
 * otherwise. HU-20 · {@link OrderMails} does: the customer gets an email when it becomes PAGADA or ENVIADA.
 */
public record OrderStatusChanged(Long orderId, OrderStatus status) {
}
