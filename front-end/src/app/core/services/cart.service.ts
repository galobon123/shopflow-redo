import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CartItem, CartMerge } from '../models/cart.model';

/** HU-12 · This browser's guest cart. Kept across reloads; a new one after it joins an account. */
const CART_ID_KEY = 'cart_id';

@Injectable({
  providedIn: 'root'
})
export class CartService {

  private url = `${environment.apiUrl}/cart`;

  constructor(private http: HttpClient) {}

  getCart(): Observable<CartItem[]> {
    return this.http.get<CartItem[]>(this.url, { headers: this.headers() });
  }

  add(productId: number, qty: number = 1): Observable<void> {
    return this.http.post<void>(`${this.url}/${productId}?qty=${qty}`, null, { headers: this.headers() });
  }

  updateQty(productId: number, qty: number): Observable<void> {
    return this.http.put<void>(`${this.url}/${productId}?qty=${qty}`, null, { headers: this.headers() });
  }

  remove(productId: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${productId}`, { headers: this.headers() });
  }

  clear(): Observable<void> {
    return this.http.delete<void>(this.url, { headers: this.headers() });
  }

  /** HU-12 · Right after signing in: the guest cart joins the account's. */
  merge(): Observable<CartMerge> {
    return this.http.post<CartMerge>(`${this.url}/merge`, null, { headers: this.headers() });
  }

  /** HU-12 · After a merge the server deleted the guest cart: signing out starts an empty one. */
  newGuestCart(): void {
    localStorage.setItem(CART_ID_KEY, crypto.randomUUID());
  }

  /** Signed in, the server ignores it and uses the account's cart. */
  private headers(): HttpHeaders {
    let cartId = localStorage.getItem(CART_ID_KEY);
    if (!cartId) {
      cartId = crypto.randomUUID();
      localStorage.setItem(CART_ID_KEY, cartId);
    }
    return new HttpHeaders({ 'X-Cart-Id': cartId });
  }
}
