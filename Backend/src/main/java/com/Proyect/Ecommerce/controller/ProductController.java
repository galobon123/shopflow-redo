package com.Proyect.Ecommerce.controller;

import jakarta.validation.Valid;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.Proyect.Ecommerce.dto.PageResponse;
import com.Proyect.Ecommerce.dto.product.ProductDetailDTO;
import com.Proyect.Ecommerce.dto.product.ProductRequestDTO;
import com.Proyect.Ecommerce.dto.product.ProductResponseDTO;
import com.Proyect.Ecommerce.search.ProductSearchIndex;
import com.Proyect.Ecommerce.service.ProductService;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * HU-10 · Catalog: only active products, paged.
     * e.g. /api/products?category=Remeras&q=lino&sort=price_asc&page=0&size=12
     */
    @GetMapping
    public ResponseEntity<PageResponse<ProductResponseDTO>> search(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + ProductService.DEFAULT_PAGE_SIZE) int size) {
        return ResponseEntity.ok(productService.search(category, q, sort, page, size, false));
    }

    /**
     * Search as you type: the 5 active products closest to {@code q}, ignoring accents and small typos.
     * Empty until {@code q} has 3 letters. e.g. /api/products/suggestions?q=sarten
     */
    @GetMapping("/suggestions")
    public ResponseEntity<List<ProductSearchIndex.Suggestion>> suggestions(@RequestParam(defaultValue = "") String q) {
        return ResponseEntity.ok(productService.suggest(q));
    }

    /** HU-11 · Product page: 404 when it doesn't exist or is inactive, unless an admin asks. */
    @GetMapping("/{id}")
    public ResponseEntity<ProductDetailDTO> findById(@PathVariable Long id, Authentication auth) {
        boolean admin = auth != null && auth.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return ResponseEntity.ok(productService.findById(id, admin));
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateById(@PathVariable Long id) {
        productService.deactivateById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<ProductResponseDTO> save(@Valid @RequestBody ProductRequestDTO productRequest) {
        ProductResponseDTO savedProduct = productService.save(productRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedProduct);
    }

}
