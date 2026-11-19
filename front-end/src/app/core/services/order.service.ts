import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { OrderRequest, OrderResponse } from '../models/order.model';

@Injectable({
  providedIn: 'root'
})
export class OrderService {

  private url = `${environment.apiUrl}/orders`;

  constructor(private http: HttpClient) {}

  createOrder(addressId: number): Observable<OrderResponse> {
    return this.http.post<OrderResponse>(this.url, { addressId } as OrderRequest);
  }

  getMyOrders(): Observable<OrderResponse[]> {
    return this.http.get<OrderResponse[]>(this.url);
  }
}
