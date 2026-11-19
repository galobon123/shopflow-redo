package com.Proyect.Ecommerce.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.Proyect.Ecommerce.dto.PageResponse;
import com.Proyect.Ecommerce.dto.product.ProductRequestDTO;
import com.Proyect.Ecommerce.dto.product.ProductResponseDTO;
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

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> findById(@PathVariable Long id) {
        ProductResponseDTO product = productService.findById(id);
        return ResponseEntity.ok(product);
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
