import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CartItem } from '../models/cart.model';

@Injectable({
  providedIn: 'root'
})
export class CartService {

  private url = `${environment.apiUrl}/cart`;

  constructor(private http: HttpClient) {}

  getCart(): Observable<CartItem[]> {
    return this.http.get<CartItem[]>(this.url);
  }

  add(productId: number, qty: number = 1): Observable<void> {
    return this.http.post<void>(`${this.url}/${productId}?qty=${qty}`, null);
  }

  updateQty(productId: number, qty: number): Observable<void> {
    return this.http.put<void>(`${this.url}/${productId}?qty=${qty}`, null);
  }

  remove(productId: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${productId}`);
  }

  clear(): Observable<void> {
    return this.http.delete<void>(this.url);
  }
}