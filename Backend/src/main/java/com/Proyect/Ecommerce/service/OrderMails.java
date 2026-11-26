package com.Proyect.Ecommerce.service;

import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.Proyect.Ecommerce.model.Order;
import com.Proyect.Ecommerce.repository.OrderRepository;
import com.Proyect.Ecommerce.service.MailQueue.Mail;

/**
 * HU-20 · The customer's emails about their order: when it's paid and when it's shipped. Queued once Postgres
 * committed the change, so an order that rolls back sends nothing, and nothing here can undo or fail the change.
 * Nothing makes an order ENVIADA yet (HU-18's state machine and HU-23's order management will): that email is ready.
 */
@Component
public class OrderMails {

    private static final Logger log = LoggerFactory.getLogger(OrderMails.class);

    private final OrderRepository orderRepository;
    private final MailTemplates templates;
    private final MailQueue queue;

    public OrderMails(OrderRepository orderRepository, MailTemplates templates, MailQueue queue) {
        this.orderRepository = orderRepository;
        this.templates = templates;
        this.queue = queue;
    }

    /** Rendered right after the commit: the email shows the order as it was paid (or shipped), however long it waits. */
    @TransactionalEventListener
    public void onOrderStatusChanged(OrderStatusChanged event) {
        Function<Order, Mail> email = switch (event.status()) {
            case PAGADA -> templates::orderPaid;
            case ENVIADA -> templates::orderShipped;
            default -> null;
        };
        if (email == null) return;
        try {
            orderRepository.findWithItemsById(event.orderId()).map(email).ifPresent(queue::enqueue);
        } catch (RuntimeException e) {
            log.warn("Order {} is {} but its email could not be queued", event.orderId(), event.status(), e);
        }
    }
}
