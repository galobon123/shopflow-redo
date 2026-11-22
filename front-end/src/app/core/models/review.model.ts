import { Page } from './page.model';

/** HU-30 · One review as the product page shows it (only the author's first name). */
export interface Review {
  id: number;
  rating: number;
  comment: string | null;
  authorName: string;
  createdAt: string;
}

/** Visible reviews only. `average` is null without reviews; `counts` has every star, "1" to "5". */
export interface ReviewSummary {
  average: number | null;
  total: number;
  counts: Record<string, number>;
}

/** GET /products/:id/reviews. `canReview`: whoever asks received it and hasn't reviewed it yet. */
export interface ProductReviews {
  summary: ReviewSummary;
  reviews: Page<Review>;
  canReview: boolean;
}

export interface ReviewRequest {
  rating: number;
  comment?: string | null;
}
