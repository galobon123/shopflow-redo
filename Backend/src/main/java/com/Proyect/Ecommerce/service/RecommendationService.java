package com.Proyect.Ecommerce.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Proyect.Ecommerce.dto.builder.RecommendationBuilderDTO;
import com.Proyect.Ecommerce.dto.product.ProductResponseDTO;
import com.Proyect.Ecommerce.dto.recommendation.RecommendationResponseDTO;
import com.Proyect.Ecommerce.model.Product;
import com.Proyect.Ecommerce.model.nodes.ProductNode;
import com.Proyect.Ecommerce.repository.ProductRepository;
import com.Proyect.Ecommerce.repository.RecommendationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationRepository recommendationRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public RecommendationResponseDTO getRecommendations(Long userId) {
        List<ProductNode> byCategory = recommendationRepository.recommendByCategory(userId);
        List<ProductNode> collaborative = recommendationRepository.recommendCollaborative(userId);

        Set<Long> allIds = new HashSet<>();
        byCategory.forEach(n -> allIds.add(n.getId()));
        collaborative.forEach(n -> allIds.add(n.getId()));

        Map<Long, Product> productMap = allIds.isEmpty()
                ? Map.of()
                : productRepository.findAllById(allIds).stream()
                        .collect(Collectors.toMap(Product::getId, Function.identity()));

        RecommendationResponseDTO response = new RecommendationResponseDTO();
        response.setRecommended(byCategory.stream()
                .map(n -> productMap.get(n.getId()))
                .filter(Objects::nonNull)
                .map(this::toProductResponseDTO)
                .toList());
        response.setOthersBought(collaborative.stream()
                .map(n -> productMap.get(n.getId()))
                .filter(Objects::nonNull)
                .map(this::toProductResponseDTO)
                .toList());
        return response;
    }

    @Transactional("neo4jTransactionManager")
    public void recommendationAdd(RecommendationBuilderDTO dto) {
        recommendationRepository.syncWishlistAdd(
            dto.userId(), dto.userEmail(), dto.productId(), dto.productName(),
            dto.price() != null ? dto.price().doubleValue() : 0.0,
            dto.imageUrl() != null ? dto.imageUrl() : "",
            dto.categoryId(), dto.categoryName()
        );
    }

    @Transactional("neo4jTransactionManager")
    public void syncWishlistRemove(Long userId, Long productId) {
        recommendationRepository.syncWishlistRemove(userId, productId);
    }


    private ProductResponseDTO toProductResponseDTO(Product product) {
        ProductResponseDTO dto = new ProductResponseDTO();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setPrice(product.getPrice());
        dto.setStock(product.getStock());

        if (product.getCategory() != null) {
            dto.setCategoryName(product.getCategory().getName());
        }

        dto.setActive(product.isActive());
        dto.setCreatedAt(product.getCreatedAt());
        dto.setImageUrl(product.getImageUrl());
        return dto;
    }
}