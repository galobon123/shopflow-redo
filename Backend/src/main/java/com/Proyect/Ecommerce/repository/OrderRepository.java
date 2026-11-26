package com.Proyect.Ecommerce.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.Proyect.Ecommerce.model.Order;
import com.Proyect.Ecommerce.model.OrderStatus;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    /**
     * HU-19 · One page of the customer's order ids. Paging the ids and then reading those orders with their
     * items (findByIdIn) keeps the LIMIT in the database: a collection fetch in a paged query is paged in memory.
     */
    @Query("SELECT o.id FROM Order o WHERE o.user.id = :userId")
    Page<Long> findIdsByUserId(@Param("userId") Long userId, Pageable pageable);

    /** HU-19 · The orders with their items, products and address in one query; product photos come batched. */
    @EntityGraph(attributePaths = {"items", "items.product", "address", "address.city", "address.city.state"})
    List<Order> findByIdIn(Collection<Long> ids);

    /** HU-19 · One of the customer's orders, read the same way as findByIdIn. */
    @EntityGraph(attributePaths = {"items", "items.product", "address", "address.city", "address.city.state"})
    Optional<Order> findByIdAndUserId(Long id, Long userId);

    /** HU-20 · The order with what its emails show: customer, items, their products and the address. */
    @EntityGraph(attributePaths = {"user", "items", "items.product", "address", "address.city", "address.city.state"})
    Optional<Order> findWithItemsById(Long id);

    /** HU-30 · The customer's latest order in that status that has the product. */
    Optional<Order> findFirstByUserIdAndStatusAndItemsProductIdOrderByCreatedAtDesc(Long userId, OrderStatus status,
                                                                                    Long productId);

    boolean existsByUserIdAndStatusAndItemsProductId(Long userId, OrderStatus status, Long productId);

    /** HU-16 · An address an order was sent to is never changed in place. */
    boolean existsByAddressId(Long addressId);

    /** HU-15 · The order with what Neo4j needs (customer, items, their products and categories), read off any request. */
    @Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.user LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product p "
            + "LEFT JOIN FETCH p.category WHERE o.id = :id")
    Optional<Order> findForGraph(@Param("id") Long id);

    /**
     * HU-18 · Moves the order from one status to the next only if it's still in the first, in one UPDATE: of two
     * moves at once (the expiry job and an approved payment) only one wins, and the other gets 0.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Order o SET o.status = :to WHERE o.id = :id AND o.status = :from")
    int moveStatus(@Param("id") Long id, @Param("from") OrderStatus from, @Param("to") OrderStatus to);

    /*
     * HU-18 · The payment columns are written with their own UPDATE, never by saving a loaded Order: that would
     * write back the status it was read with, undoing a move another transaction just made.
     */

    @Modifying
    @Query("UPDATE Order o SET o.mpCheckoutUrl = :url WHERE o.id = :id")
    int setCheckoutUrl(@Param("id") Long id, @Param("url") String url);

    @Modifying
    @Query("UPDATE Order o SET o.mpPaymentId = :paymentId WHERE o.id = :id")
    int setPaymentId(@Param("id") Long id, @Param("paymentId") String paymentId);

    @Modifying
    @Query("UPDATE Order o SET o.paymentIssue = :issue WHERE o.id = :id")
    int setPaymentIssue(@Param("id") Long id, @Param("issue") String issue);

    /** HU-18 · Orders whose time to pay is up and that are still waiting for it. */
    @Query("SELECT o.id FROM Order o WHERE o.status = com.Proyect.Ecommerce.model.OrderStatus.PENDIENTE_PAGO "
            + "AND o.expiresAt < :now ORDER BY o.id")
    List<Long> findExpiredIds(@Param("now") LocalDateTime now);

    /** HU-15 · Orders paid, shipped or delivered (OrderStatus.isPaid), for the Neo4j backfill. */
    @Query("SELECT o.id FROM Order o WHERE o.status IN (com.Proyect.Ecommerce.model.OrderStatus.PAGADA, "
            + "com.Proyect.Ecommerce.model.OrderStatus.ENVIADA, com.Proyect.Ecommerce.model.OrderStatus.ENTREGADA)")
    List<Long> findPaidIds();
}
