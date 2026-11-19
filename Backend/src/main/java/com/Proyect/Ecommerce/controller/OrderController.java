package com.Proyect.Ecommerce.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Proyect.Ecommerce.dto.order.OrderRequestDTO;
import com.Proyect.Ecommerce.dto.order.OrderResponseDTO;
import com.Proyect.Ecommerce.service.OrderService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponseDTO> create(
            @RequestBody @Valid OrderRequestDTO request,
            Authentication auth) {
        Long userId = (Long) auth.getCredentials();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.createOrder(userId, request));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponseDTO>> myOrders(Authentication auth) {
        Long userId = (Long) auth.getCredentials();
        return ResponseEntity.ok(orderService.findByUserId(userId));
    }
}
