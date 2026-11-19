package com.Proyect.Ecommerce.repository;

import java.util.List;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import com.Proyect.Ecommerce.model.nodes.ProductNode;

public interface RecommendationRepository extends Neo4jRepository<ProductNode, Long> {
       @Query("MATCH (u:User {id: $userId})-[:WISHLISTED]->(p:Product) " +
              "MATCH (p)-[:BELONGS_TO]->(c:Category) " +
              "MATCH (rec:Product)-[:BELONGS_TO]->(c) " +
              "WHERE NOT EXISTS { MATCH (u)-[:WISHLISTED]->(rec) } " +
              "AND rec.id <> p.id " +
              "RETURN DISTINCT rec " +
              "LIMIT 10")
       List<ProductNode> recommendByCategory(@Param("userId") Long userId);

       @Query("MATCH (u:User {id: $userId})-[:WISHLISTED]->(p:Product)<-[:WISHLISTED]-(other:User) " +
              "WHERE other.id <> $userId " +
              "MATCH (other)-[:WISHLISTED]->(rec:Product) " +
              "WHERE NOT EXISTS { MATCH (u)-[:WISHLISTED]->(rec) } " +
              "AND rec.id <> p.id " +
              "RETURN rec, count(DISTINCT other) AS score " +
              "ORDER BY score DESC " +
              "LIMIT 10")
       List<ProductNode> recommendCollaborative(@Param("userId") Long userId);

       @Query("MERGE (u:User {id: $userId}) SET u.email = $email " +
              "MERGE (c:Category {id: $categoryId}) SET c.name = $categoryName " +
              "MERGE (p:Product {id: $productId}) SET p.name = $productName, p.price = $price, p.imageUrl = $imageUrl, p.categoryId = $categoryId " +
              "MERGE (p)-[:BELONGS_TO]->(c) " +
              "MERGE (u)-[:WISHLISTED]->(p)")
       void syncWishlistAdd(@Param("userId") Long userId, @Param("email") String email,
                            @Param("productId") Long productId, @Param("productName") String productName,
                            @Param("price") double price, @Param("imageUrl") String imageUrl,
                            @Param("categoryId") Long categoryId, @Param("categoryName") String categoryName);

       @Query("MATCH (u:User {id: $userId})-[w:WISHLISTED]->(p:Product {id: $productId}) DELETE w")
       void syncWishlistRemove(@Param("userId") Long userId, @Param("productId") Long productId);
}
