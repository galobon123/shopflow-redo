import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { RecommendationsResponse } from '../models/recommendation.model';

@Injectable({
  providedIn: 'root'
})
export class RecommendationService {

  private url = `${environment.apiUrl}/recommendations`;

  constructor(private http: HttpClient) {}

  getRecommendations(): Observable<RecommendationsResponse> {
    return this.http.get<RecommendationsResponse>(this.url);
  }
}