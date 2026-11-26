package com.Proyect.Ecommerce.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Proyect.Ecommerce.dto.order.OrderRequestDTO;
import com.Proyect.Ecommerce.dto.order.OrderResponseDTO;
import com.Proyect.Ecommerce.service.OrderCheckout;
import com.Proyect.Ecommerce.service.OrderService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderCheckout orderCheckout;

    public OrderController(OrderService orderService, OrderCheckout orderCheckout) {
        this.orderService = orderService;
        this.orderCheckout = orderCheckout;
    }

    /** HU-17 · 201 with the new order; 200 with the same one when the Idempotency-Key was already used. */
    @PostMapping
    public ResponseEntity<OrderResponseDTO> create(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody @Valid OrderRequestDTO request,
            Authentication auth) {
        Long userId = (Long) auth.getCredentials();
        OrderCheckout.Placed placed = orderCheckout.place(userId, idempotencyKey, request);
        return ResponseEntity.status(placed.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(placed.order());
    }

    @GetMapping
    public ResponseEntity<List<OrderResponseDTO>> myOrders(Authentication auth) {
        Long userId = (Long) auth.getCredentials();
        return ResponseEntity.ok(orderService.findByUserId(userId));
    }
}
