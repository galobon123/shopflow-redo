package com.Proyect.Ecommerce.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Proyect.Ecommerce.dto.builder.RecommendationBuilderDTO;
import com.Proyect.Ecommerce.dto.wishlist.WishListResponseDTO;
import com.Proyect.Ecommerce.model.Product;
import com.Proyect.Ecommerce.model.User;
import com.Proyect.Ecommerce.model.WishList;
import com.Proyect.Ecommerce.repository.ProductRepository;
import com.Proyect.Ecommerce.repository.UserRepository;
import com.Proyect.Ecommerce.exception.NotFoundException;
import com.Proyect.Ecommerce.repository.WishListRepository;

@Service
public class WishListService {

    private final WishListRepository wishListRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final RecommendationService recommendationService;

    public WishListService(WishListRepository wishListRepository,
                           ProductRepository productRepository,
                           UserRepository userRepository,
                           RecommendationService recommendationService) {
        this.wishListRepository = wishListRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.recommendationService = recommendationService;
    }

    public List<WishListResponseDTO> getWishlist(Long userId) {
        return wishListRepository.findByUserId(userId)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional
    public void addToWishlist(Long userId, Long productId) {
        if (wishListRepository.existsByUserIdAndProductId(userId, productId)) {
            return; // ya existe, no hacer nada
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + userId));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado: " + productId));
        WishList wishList = new WishList();
        wishList.setUser(user);
        wishList.setProduct(product);
        wishListRepository.save(wishList);
        recommendationService.recommendationAdd(
            RecommendationBuilderDTO.builder()
                .userId(user.getId())
                .userEmail(user.getEmail())
                .productId(product.getId())
                .productName(product.getName())
                .price(product.getPrice())
                .imageUrl(product.getImageUrl())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .build()
        );
    }

    @Transactional
    public void removeFromWishlist(Long userId, Long productId) {
        wishListRepository.deleteByUserIdAndProductId(userId, productId);
        recommendationService.syncWishlistRemove(userId, productId);
    }

    public boolean isInWishlist(Long userId, Long productId) {
        return wishListRepository.existsByUserIdAndProductId(userId, productId);
    }

    private WishListResponseDTO toResponseDTO(WishList wishList) {
        WishListResponseDTO dto = new WishListResponseDTO();
        dto.setId(wishList.getId());
        dto.setProductId(wishList.getProduct().getId());
        dto.setProductName(wishList.getProduct().getName());
        dto.setProductPrice(wishList.getProduct().getPrice());
        dto.setProductImageUrl(wishList.getProduct().getImageUrl());
        dto.setProductStock(wishList.getProduct().getStock());
        dto.setCreatedAt(wishList.getCreatedAt());
        return dto;
    }
}
