package com.Proyect.Ecommerce.model.nodes;

import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Node;

import jakarta.validation.constraints.NotBlank;

@Node("User")
public class UserNode {
    @Id
    private Long id;
    @NotBlank
    private String email;

    public UserNode() {}
    public UserNode(Long id, String email) {
        this.id = id;
        this.email = email;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
