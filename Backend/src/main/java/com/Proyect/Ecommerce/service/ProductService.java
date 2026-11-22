package com.Proyect.Ecommerce.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.Proyect.Ecommerce.dto.PageResponse;
import com.Proyect.Ecommerce.dto.product.ProductDetailDTO;
import com.Proyect.Ecommerce.dto.product.ProductRequestDTO;
import com.Proyect.Ecommerce.dto.product.ProductResponseDTO;
import com.Proyect.Ecommerce.dto.review.ReviewSummaryDTO;
import com.Proyect.Ecommerce.model.Category;
import com.Proyect.Ecommerce.model.Product;
import com.Proyect.Ecommerce.model.ProductImage;
import com.Proyect.Ecommerce.exception.BadRequestException;
import com.Proyect.Ecommerce.exception.NotFoundException;
import com.Proyect.Ecommerce.repository.CategoryRepository;
import com.Proyect.Ecommerce.repository.ProductRepository;
import com.Proyect.Ecommerce.repository.ProductSpecs;
import com.Proyect.Ecommerce.search.ProductSearchIndex;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductSearchIndex searchIndex;
    private final ReviewService reviewService;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          ProductSearchIndex searchIndex,
                          ReviewService reviewService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.searchIndex = searchIndex;
        this.reviewService = reviewService;
    }

    /** HU-10 · The ways the catalog can be sorted; anything else is a 400. */
    public enum SortOrder {
        NEWEST(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
        PRICE_ASC(Sort.by(Sort.Order.asc("price"), Sort.Order.asc("id"))),
        PRICE_DESC(Sort.by(Sort.Order.desc("price"), Sort.Order.asc("id"))),
        NAME(Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));

        private final Sort sort;

        SortOrder(Sort sort) {
            this.sort = sort;
        }

        static SortOrder parse(String value) {
            if (value == null || value.isBlank()) {
                return NEWEST;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Orden no válido: " + value + ". Usá newest, price_asc, price_desc o name.");
            }
        }
    }

    public static final int DEFAULT_PAGE_SIZE = 12;
    public static final int MAX_PAGE_SIZE = 48;
    public static final int SUGGESTIONS = 5;

    /**
     * HU-10 · Catalog search. Customers only see active products ({@code includeInactive} is for the admin).
     * {@code category} and {@code text} are optional; {@code page} starts at 0 and {@code size} is capped at 48.
     */
    public PageResponse<ProductResponseDTO> search(String category, String text, String sort,
                                                   int page, int size, boolean includeInactive) {
        if (page < 0) {
            throw new BadRequestException("La página no puede ser negativa.");
        }
        if (size < 1) {
            throw new BadRequestException("El tamaño de página tiene que ser al menos 1.");
        }
        List<Specification<Product>> filters = new ArrayList<>();
        if (!includeInactive) {
            filters.add(ProductSpecs.active());
        }
        if (category != null && !category.isBlank()) {
            filters.add(ProductSpecs.inCategory(category.trim()));
        }
        if (text != null && !text.isBlank()) {
            // The exact text anywhere in the name or description, or a name the suggestions would offer
            // ("sarten", "sarteb" -> "Sartén de hierro").
            filters.add(ProductSpecs.matching(text).or(ProductSpecs.withIdIn(searchIndex.matchingIds(text))));
        }
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), SortOrder.parse(sort).sort);
        return PageResponse.of(productRepository.findAll(Specification.allOf(filters), pageable), this::toResponseDTO);
    }

    public List<ProductSearchIndex.Suggestion> suggest(String query) {
        return searchIndex.suggest(query, SUGGESTIONS);
    }

    /**
     * HU-11 · Product page. An inactive product is as missing as one that never existed, except for the admin.
     */
    public ProductDetailDTO findById(Long id, boolean includeInactive) {
        Product product = productRepository.findById(id)
                .filter(p -> includeInactive || p.isActive())
                .orElseThrow(() -> new NotFoundException("Este producto no existe o ya no está disponible."));
        ProductDetailDTO dto = new ProductDetailDTO();
        fill(dto, product);
        dto.setImages(product.getImages().stream().map(ProductImage::getUrl).toList());
        addReviewSummary(dto);
        return dto;
    }

    /** HU-30 · Average stars and how many reviews, hidden ones left out. */
    private void addReviewSummary(ProductDetailDTO dto) {
        ReviewSummaryDTO summary = reviewService.summary(dto.getId());
        dto.setAverageRating(summary.average());
        dto.setReviewCount(summary.total());
    }

    public ProductResponseDTO save(ProductRequestDTO dto) {
        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new NotFoundException("Categoría no encontrada: " + dto.getCategoryId()));
        Product product = new Product();
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock());
        product.setCategory(category);
        return toResponseDTO(productRepository.save(product));
    }

    public void deactivateById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado: " + id));
        product.setActive(false);
        productRepository.save(product);
    }

    private ProductResponseDTO toResponseDTO(Product product) {
        ProductResponseDTO dto = new ProductResponseDTO();
        fill(dto, product);
        return dto;
    }

    private void fill(ProductResponseDTO dto, Product product) {
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setPrice(product.getPrice());
        dto.setStock(product.getStock());
        dto.setCategoryName(product.getCategory().getName());
        dto.setActive(product.isActive());
        dto.setCreatedAt(product.getCreatedAt());
        dto.setImageUrl(product.getImageUrl());
    }
}
