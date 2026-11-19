package com.Proyect.Ecommerce.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Proyect.Ecommerce.model.WishList;

@Repository
public interface WishListRepository extends JpaRepository<WishList, Long> {
    List<WishList> findByUserId(Long userId);
    Optional<WishList> findByUserIdAndProductId(Long userId, Long productId);
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    void deleteByUserIdAndProductId(Long userId, Long productId);   
}
