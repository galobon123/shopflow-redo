package com.Proyect.Ecommerce.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.Proyect.Ecommerce.repository.OrderRepository;

/**
 * HU-18 · Every minute ({@code app.orders.expiry-cron}) cancels the orders still waiting for their payment after
 * their 30 minutes and gives their stock back. Each one in its own transaction through
 * {@link OrderService#cancelExpired}, which only moves it if it's still PENDIENTE_PAGO: an approved payment that
 * arrives at the same moment either gets there first (the order stays paid) or finds it cancelled
 * (PaymentService marks it for review).
 */
@Component
public class OrderExpiry {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiry.class);

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    public OrderExpiry(OrderRepository orderRepository, OrderService orderService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    @Scheduled(cron = "${app.orders.expiry-cron}")
    public void cancelExpired() {
        cancelExpired(LocalDateTime.now());
    }

    /**
     * Cancels what's expired at {@code now}, the JVM's local time like {@code expires_at} (UTC, see
     * EcommerceApplication). Tests pass a later time instead of waiting 30 minutes. Returns how many it cancelled.
     */
    public int cancelExpired(LocalDateTime now) {
        int cancelled = 0;
        for (Long orderId : orderRepository.findExpiredIds(now)) {
            try {
                if (orderService.cancelExpired(orderId)) cancelled++;
            } catch (RuntimeException e) {
                log.warn("Expired order {} could not be cancelled, the next run tries again", orderId, e);
            }
        }
        if (cancelled > 0) log.info("Cancelled {} unpaid orders whose time to pay was up", cancelled);
        return cancelled;
    }
}
