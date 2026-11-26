package com.Proyect.Ecommerce.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.Proyect.Ecommerce.model.WishList;

@Repository
public interface WishListRepository extends JpaRepository<WishList, Long> {
    List<WishList> findByUserId(Long userId);
    Optional<WishList> findByUserIdAndProductId(Long userId, Long productId);
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    void deleteByUserIdAndProductId(Long userId, Long productId);

    /** HU-14 · The entry with what Neo4j needs (customer, product, category), read off any request. */
    @Query("SELECT w FROM WishList w JOIN FETCH w.user JOIN FETCH w.product p JOIN FETCH p.category "
            + "WHERE w.user.id = :userId AND w.product.id = :productId")
    Optional<WishList> findForGraph(@Param("userId") Long userId, @Param("productId") Long productId);

    /** HU-14 · Empties it in one statement and says what it removed, so each one can be synced to Neo4j. */
    @Query(value = "DELETE FROM wishlist WHERE user_id = :userId RETURNING product_id", nativeQuery = true)
    List<Long> deleteAllOfUser(@Param("userId") Long userId);

    /** HU-15 · Every (customer, product) pair, for the Neo4j backfill. */
    @Query("SELECT w.user.id AS userId, w.product.id AS productId FROM WishList w")
    List<Pair> findAllPairs();

    interface Pair {
        Long getUserId();
        Long getProductId();
    }
}
