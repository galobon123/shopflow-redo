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