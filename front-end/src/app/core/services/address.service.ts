import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AddressRequest, AddressResponse } from '../models/address.model';

@Injectable({
  providedIn: 'root'
})
export class AddressService {

  private url = `${environment.apiUrl}/addresses`;

  constructor(private http: HttpClient) {}

  getAddresses(): Observable<AddressResponse[]> {
    return this.http.get<AddressResponse[]>(this.url);
  }

  createAddress(address: AddressRequest): Observable<AddressResponse> {
    return this.http.post<AddressResponse>(this.url, address);
  }

  deleteAddress(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
