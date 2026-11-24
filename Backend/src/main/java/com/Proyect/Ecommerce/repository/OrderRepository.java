package com.Proyect.Ecommerce.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /** HU-15 · The order with what Neo4j needs (customer, items, their products and categories), read off any request. */
    @Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.user LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product p "
            + "LEFT JOIN FETCH p.category WHERE o.id = :id")
    Optional<Order> findForGraph(@Param("id") Long id);

    /** HU-15 · Orders paid, shipped or delivered (OrderStatus.isPaid), for the Neo4j backfill. */
    @Query("SELECT o.id FROM Order o WHERE o.status IN (com.Proyect.Ecommerce.model.OrderStatus.PAGADA, "
            + "com.Proyect.Ecommerce.model.OrderStatus.ENVIADA, com.Proyect.Ecommerce.model.OrderStatus.ENTREGADA)")
    List<Long> findPaidIds();
}
