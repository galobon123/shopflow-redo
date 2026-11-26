import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MyReview, ProductReviews, Review, ReviewRequest } from '../models/review.model';

/** HU-30 · Reviews of a product. */
@Injectable({
  providedIn: 'root'
})
export class ReviewService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/products`;

  /** One page (starting at 0), newest first; `rating` keeps only the reviews with that many stars. */
  list(productId: number, rating: number | null, page: number): Observable<ProductReviews> {
    let params = new HttpParams().set('page', page);
    if (rating !== null) params = params.set('rating', rating);
    return this.http.get<ProductReviews>(`${this.url}/${productId}/reviews`, { params });
  }

  /** 403 if the person didn't receive the product, 409 if they already reviewed it. */
  create(productId: number, review: ReviewRequest): Observable<Review> {
    return this.http.post<Review>(`${this.url}/${productId}/reviews`, review);
  }

  /** The signed-in person's own review; 404 if they haven't reviewed the product. */
  update(productId: number, review: ReviewRequest): Observable<MyReview> {
    return this.http.put<MyReview>(`${this.url}/${productId}/reviews/mine`, review);
  }
}
