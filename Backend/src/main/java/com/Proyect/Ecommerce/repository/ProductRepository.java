package com.Proyect.Ecommerce.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.Proyect.Ecommerce.model.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /** Loads the category with each product: the listing shows its name (no N+1). */
    @Override
    @EntityGraph(attributePaths = "category")
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    /** Only what the search index needs from each active product. */
    interface SearchView {
        Long getId();
        String getName();
        BigDecimal getPrice();
        String getImageUrl();
    }

    /** HU-11 · The photo is the cover (position 0), now in product_images. */
    @Query("SELECT p.id AS id, p.name AS name, p.price AS price, i.url AS imageUrl "
            + "FROM Product p LEFT JOIN p.images i ON i.position = 0 WHERE p.active = true")
    List<SearchView> findByActiveTrue();

    /** Takes {@code quantity} units only if they are there. Returns 0 when there isn't enough stock. */
    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock - :quantity WHERE p.id = :id AND p.stock >= :quantity")
    int decrementStock(@Param("id") Long id, @Param("quantity") int quantity);

    /** HU-18 · Gives back the units an order took. Called once, by the move to CANCELADA that won. */
    @Modifying
    @Query(value = """
            UPDATE products p SET stock = p.stock + taken.units
            FROM (SELECT product_id, sum(quantity) AS units FROM order_items WHERE order_id = :orderId
                  GROUP BY product_id) taken
            WHERE p.id = taken.product_id
            """, nativeQuery = true)
    int restock(@Param("orderId") Long orderId);

    /** HU-15 · Recommended products with their category (no N+1); the caller puts them back in ranking order. */
    @EntityGraph(attributePaths = "category")
    List<Product> findByIdIn(Collection<Long> ids);

    /** HU-15 · The product with its category, for its Neo4j node. */
    @Query("SELECT p FROM Product p JOIN FETCH p.category WHERE p.id = :id")
    Optional<Product> findForGraph(@Param("id") Long id);

    @Query("SELECT p.id FROM Product p")
    List<Long> findAllIds();

    /**
     * HU-15 · "Más vendidos": active products with stock, by units sold in orders paid or later
     * (OrderStatus.isPaid); unsold ones after them, newest first, so a store without sales still shows something.
     */
    @Query(value = """
            SELECT p.id FROM products p
            LEFT JOIN (SELECT i.product_id, sum(i.quantity) AS units
                       FROM order_items i JOIN orders o ON o.id = i.order_id
                       WHERE o.status IN ('PAGADA', 'ENVIADA', 'ENTREGADA')
                       GROUP BY i.product_id) sold ON sold.product_id = p.id
            WHERE p.active AND p.stock > 0
            ORDER BY coalesce(sold.units, 0) DESC, p.created_at DESC, p.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findBestSellerIds(@Param("limit") int limit);
}
