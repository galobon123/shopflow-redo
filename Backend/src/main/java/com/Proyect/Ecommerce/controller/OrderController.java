package com.Proyect.Ecommerce.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Proyect.Ecommerce.dto.PageResponse;
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

    /** HU-19 · "Mis pedidos", newest first; e.g. /api/orders?page=1 (pages start at 0). */
    @GetMapping
    public ResponseEntity<PageResponse<OrderResponseDTO>> myOrders(@RequestParam(defaultValue = "0") int page,
                                                                   Authentication auth) {
        Long userId = (Long) auth.getCredentials();
        return ResponseEntity.ok(orderService.findPage(userId, page));
    }

    /** HU-19 · One of the customer's orders; someone else's or a missing one is the same 404. */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponseDTO> myOrder(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getCredentials();
        return ResponseEntity.ok(orderService.findOwn(userId, id));
    }
}
