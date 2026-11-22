package com.Proyect.Ecommerce.repository;

import java.math.BigDecimal;
import java.util.List;

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
}
