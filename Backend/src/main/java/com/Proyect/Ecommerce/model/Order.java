package com.Proyect.Ecommerce.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Table(name = "orders")
@NoArgsConstructor
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private BigDecimal total;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id", nullable = false)
    private Address address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDIENTE_PAGO;

    /** Until when an unpaid order holds its stock (set by HU-17). */
    private LocalDateTime expiresAt;

    /** HU-18 · Where the customer pays in Mercado Pago: one preference per order, reused on every attempt. */
    private String mpCheckoutUrl;

    /** HU-18 · The Mercado Pago payment that paid it. */
    private String mpPaymentId;

    /** HU-18 · Why its payment needs someone to look at it (e.g. approved after it was cancelled); null if nothing. */
    private String paymentIssue;

    /** HU-19 · In the order they were bought, so the receipt and "Mis pedidos" list them the same way every time. */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<OrderItem> items;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
