package com.Proyect.Ecommerce.model.nodes;

import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Node;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Node("Product")
@Getter
@Setter
public class ProductNode {
    @Id
    private Long id;
    @NotBlank
    private String name;
    @NotNull
    private Double price;
    private String imageUrl;
    @NotNull
    private Long categoryId;

    public ProductNode() {}
    public ProductNode(Long id, String name, Double price, String imageUrl, Long categoryId) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.imageUrl = imageUrl;
        this.categoryId = categoryId;
    }
}
