package com.Proyect.Ecommerce.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Proyect.Ecommerce.model.Order;
import com.Proyect.Ecommerce.model.OrderStatus;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** HU-30 · The customer's latest order in that status that has the product. */
    Optional<Order> findFirstByUserIdAndStatusAndItemsProductIdOrderByCreatedAtDesc(Long userId, OrderStatus status,
                                                                                    Long productId);

    boolean existsByUserIdAndStatusAndItemsProductId(Long userId, OrderStatus status, Long productId);
}
