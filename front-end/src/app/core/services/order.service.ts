import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { OrderRequest, OrderResponse } from '../models/order.model';
import { Page } from '../models/page.model';

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

  /** HU-19 · "Mis pedidos", newest first. `page` starts at 0. */
  getMyOrders(page: number): Observable<Page<OrderResponse>> {
    return this.http.get<Page<OrderResponse>>(this.url, { params: new HttpParams().set('page', page) });
  }

  /** HU-19 · One of the customer's orders; someone else's is a 404, like a missing one. */
  getById(id: number): Observable<OrderResponse> {
    return this.http.get<OrderResponse>(`${this.url}/${id}`);
  }
}
