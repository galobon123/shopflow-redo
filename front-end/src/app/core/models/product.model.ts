export interface ProductRequest {
  name: string;
  description?: string;
  price: number;
  stock: number;
  categoryId: number;
  imageUrl?: string;
}

/** One of the 5 search-as-you-type suggestions under the search box. */
export interface ProductSuggestion {
  id: number;
  name: string;
  price: number;
  imageUrl?: string | null;
}

export interface ProductResponse {
  id: number;
  name: string;
  description: string;
  price: number;
  stock: number;
  categoryName: string;
  active: boolean;
  createdAt: string;
  imageUrl?: string;
}

/** HU-11 · Product page: the card's data plus every photo and the review summary. */
export interface ProductDetail extends ProductResponse {
  /** Photo URLs ordered by position; the first one is the cover. */
  images: string[];
  /** HU-30 · Average stars; null while the product has no reviews. */
  averageRating: number | null;
  reviewCount: number;
}
