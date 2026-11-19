import { ProductResponse } from './product.model';

export interface RecommendationsResponse {
  recommended: ProductResponse[];
  othersBought: ProductResponse[];
}