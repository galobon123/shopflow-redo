package com.Proyect.Ecommerce.model.nodes;

import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Node;

import jakarta.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

@Node("Category")
@Getter
@Setter
public class CategoryNode {
    @Id
    private Long id;
    @NotBlank
    private String name;

    public CategoryNode() {}
    public CategoryNode(Long id, String name) {
        this.id = id;
        this.name = name;
    }
}

