package com.Proyect.Ecommerce.service;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ApplicationEventPublisher events;

    public WishListService(WishListRepository wishListRepository,
                           ProductRepository productRepository,
                           UserRepository userRepository,
                           ApplicationEventPublisher events) {
        this.wishListRepository = wishListRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.events = events;
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
        // HU-14 · Neo4j is synced after the commit (GraphSync): if it's down, the wishlist still saves.
        events.publishEvent(new WishlistChanged(userId, productId));
    }

    @Transactional
    public void removeFromWishlist(Long userId, Long productId) {
        wishListRepository.deleteByUserIdAndProductId(userId, productId);
        events.publishEvent(new WishlistChanged(userId, productId));
    }

    /** HU-14 · All or nothing, in one statement; each product removed is then taken out of Neo4j too. */
    @Transactional
    public void clearWishlist(Long userId) {
        wishListRepository.deleteAllOfUser(userId)
                .forEach(productId -> events.publishEvent(new WishlistChanged(userId, productId)));
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
