package com.Proyect.Ecommerce.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.Proyect.Ecommerce.dto.product.ProductResponseDTO;
import com.Proyect.Ecommerce.dto.recommendation.RecommendationResponseDTO;
import com.Proyect.Ecommerce.dto.recommendation.RecommendationResponseDTO.Section;
import com.Proyect.Ecommerce.dto.recommendation.RecommendationResponseDTO.Type;
import com.Proyect.Ecommerce.model.Product;
import com.Proyect.Ecommerce.repository.ProductRepository;

/**
 * HU-15 · What the store recommends. Neo4j ranks (through {@link RecommendationService}) and Postgres decides
 * what can be shown right now: active and in stock, so a product sold out a second ago is already left out.
 * <p>
 * The customer's ranking is kept in Redis at {@code recs:{userId}} for {@link #TTL}: only ids, so prices and
 * stock are always fresh. {@link GraphSync} forgets it once a purchase or a wishlist change reaches Neo4j.
 * When a read from Neo4j fails, {@value #DOWN} marks it down for just {@link #DOWN_TTL}: that minute no page
 * waits for a dead Neo4j, and the personalized blocks come back soon after it does. Meanwhile, and for
 * customers without history, the answer is "Más vendidos", from Postgres.
 * <p>
 * No transaction here on purpose: Neo4j is asked before Postgres is touched, so a slow Neo4j never holds a
 * Postgres connection the catalog, cart or checkout need (with open-in-view, the request keeps the first
 * connection it takes until it ends).
 */
@Service
public class RecommendedProducts {

    private static final Logger log = LoggerFactory.getLogger(RecommendedProducts.class);

    static final String CACHE = "recs:";
    static final Duration TTL = Duration.ofMinutes(10);
    static final Duration DOWN_TTL = Duration.ofMinutes(1);
    /** One mark for every reader: product pages and customers alike skip Neo4j while it's there. */
    static final String DOWN = "recs:neo4j-down";
    /** Products in each block. */
    static final int SHOWN = 8;
    /** Asked for more than shown, so the inactive or sold-out ones can drop out and the next ones fill in. */
    private static final int CANDIDATES = 40;

    private final RecommendationService graph;
    private final ProductRepository productRepository;
    private final StringRedisTemplate redis;

    public RecommendedProducts(RecommendationService graph, ProductRepository productRepository,
                               StringRedisTemplate redis) {
        this.graph = graph;
        this.productRepository = productRepository;
        this.redis = redis;
    }

    /** HU-15 · The customer's blocks; never empty while the store has something to sell. */
    public RecommendationResponseDTO forCustomer(Long userId) {
        List<Section> sections = new ArrayList<>();
        Set<Long> shown = new HashSet<>();
        ranking(userId).forEach((type, ids) -> {
            List<ProductResponseDTO> products = showable(ids, shown);
            if (!products.isEmpty()) sections.add(new Section(type, products));
        });
        if (sections.isEmpty()) {
            sections.add(new Section(Type.BEST_SELLERS, showable(productRepository.findBestSellerIds(SHOWN), shown)));
        }
        return new RecommendationResponseDTO(sections);
    }

    /**
     * HU-15 · "Otros también compraron" on a product page, the same for everyone (guests too). Without Neo4j the
     * block just isn't there: best sellers under that title would say something false.
     */
    public List<ProductResponseDTO> alsoBought(Long productId) {
        if (neo4jDown()) return List.of();
        List<Long> ids;
        try {
            ids = graph.alsoBought(productId, CANDIDATES);
        } catch (RuntimeException e) {
            log.warn("Neo4j failed for 'also bought' of product {}, block left empty: {}", productId, e.toString());
            markNeo4jDown();
            return List.of();
        }
        return showable(ids, new HashSet<>(Set.of(productId)));
    }

    /** HU-15 · The next request asks Neo4j again. */
    public void forget(Long userId) {
        redis.delete(CACHE + userId);
    }

    /** Neo4j's ranking per block, from the cache when it's there. Empty when Neo4j failed. */
    private Map<Type, List<Long>> ranking(Long userId) {
        String key = CACHE + userId;
        String cached = redis.opsForValue().get(key);
        if (cached != null) return parse(cached);
        if (neo4jDown()) return Map.of();
        Map<Type, List<Long>> ranking = new LinkedHashMap<>();
        try {
            ranking.put(Type.FOR_YOU, graph.boughtByBuyersLikeYou(userId, CANDIDATES));
            ranking.put(Type.WISHLIST, graph.likeYourWishlist(userId, CANDIDATES));
        } catch (RuntimeException e) {
            log.warn("Neo4j failed for the recommendations of user {}, best sellers instead: {}", userId, e.toString());
            markNeo4jDown();
            return Map.of();
        }
        redis.opsForValue().set(key, format(ranking), TTL);
        return ranking;
    }

    private boolean neo4jDown() {
        return Boolean.TRUE.equals(redis.hasKey(DOWN));
    }

    private void markNeo4jDown() {
        redis.opsForValue().set(DOWN, "1", DOWN_TTL);
    }

    /** In ranking order: active, in stock and not already in an earlier block; at most {@link #SHOWN}. */
    private List<ProductResponseDTO> showable(List<Long> ids, Set<Long> shown) {
        if (ids.isEmpty()) return List.of();
        Map<Long, Product> products = productRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<ProductResponseDTO> result = ids.stream()
                .filter(id -> !shown.contains(id))
                .map(products::get)
                .filter(Objects::nonNull)
                .filter(product -> product.isActive() && product.getStock() > 0)
                .limit(SHOWN)
                .map(RecommendedProducts::toResponseDTO)
                .toList();
        result.forEach(product -> shown.add(product.getId()));
        return result;
    }

    /** "FOR_YOU=5,7;WISHLIST=" */
    private static String format(Map<Type, List<Long>> ranking) {
        return ranking.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue().stream().map(String::valueOf)
                        .collect(Collectors.joining(",")))
                .collect(Collectors.joining(";"));
    }

    private static Map<Type, List<Long>> parse(String cached) {
        Map<Type, List<Long>> ranking = new LinkedHashMap<>();
        for (String block : cached.split(";")) {
            String[] parts = block.split("=", 2);
            ranking.put(Type.valueOf(parts[0]), parts[1].isEmpty() ? List.of()
                    : Arrays.stream(parts[1].split(",")).map(Long::valueOf).toList());
        }
        return ranking;
    }

    private static ProductResponseDTO toResponseDTO(Product product) {
        ProductResponseDTO dto = new ProductResponseDTO();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setPrice(product.getPrice());
        dto.setStock(product.getStock());
        dto.setCategoryName(product.getCategory().getName());
        dto.setActive(product.isActive());
        dto.setCreatedAt(product.getCreatedAt());
        dto.setImageUrl(product.getImageUrl());
        return dto;
    }
}
