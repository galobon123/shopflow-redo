import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ProductRequest, ProductResponse, ProductSuggestion } from '../models/product.model';
import { Page } from '../models/page.model';

export type ProductSort = 'newest' | 'price_asc' | 'price_desc' | 'name';

/** HU-10 · Catalog query. `page` starts at 0, like the API. */
export interface CatalogQuery {
  category?: string | null;
  q?: string | null;
  sort?: ProductSort;
  page?: number;
  size?: number;
}

@Injectable({
  providedIn: 'root'
})
export class ProductService {

  private url = `${environment.apiUrl}/products`;

  constructor(private http: HttpClient) {}

  /** Active products only, one page at a time. Empty filters are left out of the URL. */
  search(query: CatalogQuery): Observable<Page<ProductResponse>> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(query)) {
      if (value !== null && value !== undefined && value !== '') params = params.set(key, String(value));
    }
    return this.http.get<Page<ProductResponse>>(this.url, { params });
  }

  /** The closest active products to what is being typed (accents and small typos don't matter). */
  suggest(q: string): Observable<ProductSuggestion[]> {
    return this.http.get<ProductSuggestion[]>(`${this.url}/suggestions`, { params: { q } });
  }

  getById(id: number): Observable<ProductResponse> {
    return this.http.get<ProductResponse>(`${this.url}/${id}`);
  }

  create(product: ProductRequest): Observable<ProductResponse> {
    return this.http.post<ProductResponse>(this.url, product);
  }

  deactivate(id: number): Observable<void> {
    return this.http.put<void>(`${this.url}/${id}/deactivate`, null);
  }
}
