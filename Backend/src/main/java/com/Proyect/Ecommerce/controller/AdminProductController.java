package com.Proyect.Ecommerce.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Proyect.Ecommerce.dto.PageResponse;
import com.Proyect.Ecommerce.dto.product.ProductResponseDTO;
import com.Proyect.Ecommerce.service.ProductService;

/** HU-10/HU-21 · The admin sees every product, inactive ones included. Admin only (SecurityConfig). */
@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final ProductService productService;

    public AdminProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ProductResponseDTO>> search(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + ProductService.DEFAULT_PAGE_SIZE) int size) {
        return ResponseEntity.ok(productService.search(category, q, sort, page, size, true));
    }
}
