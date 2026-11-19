package com.Proyect.Ecommerce.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.Proyect.Ecommerce.model.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByName(String name);

    List<Product> findByActiveTrue();

    List<Product> findByPriceBetween(BigDecimal min, BigDecimal max);

    List<Product> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    List<Product> findByCategoryId(Long categoryId);

    /** Takes {@code quantity} units only if they are there. Returns 0 when there isn't enough stock. */
    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock - :quantity WHERE p.id = :id AND p.stock >= :quantity")
    int decrementStock(@Param("id") Long id, @Param("quantity") int quantity);
}
