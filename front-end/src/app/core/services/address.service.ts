import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AddressRequest, AddressResponse, CityResponse, StateResponse } from '../models/address.model';

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

  /** HU-16 · Public and cached by the browser for a day (Cache-Control from the backend). */
  getStates(): Observable<StateResponse[]> {
    return this.http.get<StateResponse[]>(`${environment.apiUrl}/states`);
  }

  getCities(stateId: number): Observable<CityResponse[]> {
    return this.http.get<CityResponse[]>(`${environment.apiUrl}/cities`, { params: new HttpParams().set('stateId', stateId) });
  }
}
