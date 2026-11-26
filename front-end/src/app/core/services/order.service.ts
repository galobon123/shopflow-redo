import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { OrderRequest, OrderResponse } from '../models/order.model';

@Injectable({
  providedIn: 'root'
})
export class OrderService {

  private url = `${environment.apiUrl}/orders`;

  constructor(private http: HttpClient) {}

  /**
   * HU-17 · `idempotencyKey` names this purchase attempt: sent again with the same key, the server answers the
   * same order (200) instead of buying twice.
   */
  createOrder(addressId: number, idempotencyKey: string): Observable<OrderResponse> {
    return this.http.post<OrderResponse>(this.url, { addressId } as OrderRequest, {
      headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }),
    });
  }

  getMyOrders(): Observable<OrderResponse[]> {
    return this.http.get<OrderResponse[]>(this.url);
  }
}
