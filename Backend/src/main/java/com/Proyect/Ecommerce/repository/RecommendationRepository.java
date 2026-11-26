package com.Proyect.Ecommerce.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import com.Proyect.Ecommerce.model.nodes.ProductNode;

/**
 * HU-14/HU-15 · The graph: (:User)-[:WISHLISTED|PURCHASED]->(:Product)-[:BELONGS_TO]->(:Category).
 * The reads answer product ids, best first; whether they're active and in stock is Postgres' to say.
 */
public interface RecommendationRepository extends Neo4jRepository<ProductNode, Long> {

       /** HU-15 · "Otros también compraron": what the buyers of this product bought too, by how many of them. */
       @Query("MATCH (:Product {id: $productId})<-[:PURCHASED]-(other:User)-[:PURCHASED]->(rec:Product) " +
              "WHERE rec.id <> $productId " +
              "WITH rec.id AS id, count(DISTINCT other) AS buyers " +
              "ORDER BY buyers DESC, id " +
              "LIMIT $limit " +
              "RETURN id")
       List<Long> alsoBought(@Param("productId") Long productId, @Param("limit") int limit);

       /**
        * HU-15 · "Recomendados para vos": what the customers who bought what you bought also bought, minus what
        * you already bought or saved.
        */
       @Query("MATCH (u:User {id: $userId})-[:PURCHASED]->(:Product)<-[:PURCHASED]-(other:User)-[:PURCHASED]->(rec:Product) " +
              "WHERE other.id <> $userId " +
              "AND NOT EXISTS { MATCH (u)-[:PURCHASED]->(rec) } " +
              "AND NOT EXISTS { MATCH (u)-[:WISHLISTED]->(rec) } " +
              "WITH rec.id AS id, count(DISTINCT other) AS buyers " +
              "ORDER BY buyers DESC, id " +
              "LIMIT $limit " +
              "RETURN id")
       List<Long> boughtByBuyersLikeYou(@Param("userId") Long userId, @Param("limit") int limit);

       /** HU-15 · "Por tus favoritos": other products of the categories you saved, minus the saved and bought ones. */
       @Query("MATCH (u:User {id: $userId})-[:WISHLISTED]->(:Product)-[:BELONGS_TO]->(:Category)<-[:BELONGS_TO]-(rec:Product) " +
              "WHERE NOT EXISTS { MATCH (u)-[:WISHLISTED]->(rec) } " +
              "AND NOT EXISTS { MATCH (u)-[:PURCHASED]->(rec) } " +
              "WITH rec.id AS id, count(*) AS score " +
              "ORDER BY score DESC, id " +
              "LIMIT $limit " +
              "RETURN id")
       List<Long> likeYourWishlist(@Param("userId") Long userId, @Param("limit") int limit);

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

       /**
        * HU-15 · One PURCHASED per order and product, so syncing an order again changes nothing and two orders
        * of the same product stay two purchases. Each item: productId, name, price, imageUrl, categoryId,
        * categoryName, qty.
        */
       @Query("MERGE (u:User {id: $userId}) SET u.email = $email " +
              "WITH u UNWIND $items AS item " +
              "MERGE (c:Category {id: item.categoryId}) SET c.name = item.categoryName " +
              "MERGE (p:Product {id: item.productId}) SET p.name = item.name, p.price = item.price, p.imageUrl = item.imageUrl, p.categoryId = item.categoryId " +
              "MERGE (p)-[:BELONGS_TO]->(c) " +
              "MERGE (u)-[r:PURCHASED {orderId: $orderId}]->(p) SET r.qty = item.qty, r.at = $at")
       void syncPurchases(@Param("userId") Long userId, @Param("email") String email, @Param("orderId") Long orderId,
                          @Param("at") LocalDateTime at, @Param("items") List<Map<String, Object>> items);

       @Query("MATCH (:User)-[r:PURCHASED {orderId: $orderId}]->(:Product) DELETE r")
       void removePurchases(@Param("orderId") Long orderId);

       /** HU-15 · The product's node as Postgres has it; a category change moves its BELONGS_TO. */
       @Query("MERGE (p:Product {id: $productId}) SET p.name = $productName, p.price = $price, p.imageUrl = $imageUrl, p.categoryId = $categoryId " +
              "WITH p OPTIONAL MATCH (p)-[old:BELONGS_TO]->(other:Category) WHERE other.id <> $categoryId DELETE old " +
              "WITH DISTINCT p MERGE (c:Category {id: $categoryId}) SET c.name = $categoryName " +
              "MERGE (p)-[:BELONGS_TO]->(c)")
       void syncProduct(@Param("productId") Long productId, @Param("productName") String productName,
                        @Param("price") double price, @Param("imageUrl") String imageUrl,
                        @Param("categoryId") Long categoryId, @Param("categoryName") String categoryName);
}
