import { ProductResponse } from './product.model';

/**
 * HU-15 · FOR_YOU ("Recomendados para vos") and WISHLIST ("Por tus favoritos") are personalized; BEST_SELLERS
 * ("Más vendidos") comes instead when there's no history or Neo4j didn't answer.
 */
export type RecommendationType = 'FOR_YOU' | 'WISHLIST' | 'BEST_SELLERS';

export interface RecommendationSection {
  type: RecommendationType;
  products: ProductResponse[];
}

export interface RecommendationsResponse {
  sections: RecommendationSection[];
}
