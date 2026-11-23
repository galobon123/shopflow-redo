import { Page } from './page.model';

/** HU-30 · One review as the product page shows it (only the author's first name). */
export interface Review {
  id: number;
  rating: number;
  comment: string | null;
  authorName: string;
  createdAt: string;
  /** The author changed it after publishing. */
  edited: boolean;
}

/** The review of whoever asks. `hidden`: an admin hid it, only its author sees it. */
export interface MyReview {
  id: number;
  rating: number;
  comment: string | null;
  createdAt: string;
  edited: boolean;
  hidden: boolean;
}

/** Visible reviews only. `average` is null without reviews; `counts` has every star, "1" to "5". */
export interface ReviewSummary {
  average: number | null;
  total: number;
  counts: Record<string, number>;
}

/**
 * GET /products/:id/reviews. `canReview`: whoever asks received it and hasn't reviewed it yet.
 * `mine`: their own review, null for guests and for someone who didn't review it.
 */
export interface ProductReviews {
  summary: ReviewSummary;
  reviews: Page<Review>;
  canReview: boolean;
  mine: MyReview | null;
}

export interface ReviewRequest {
  rating: number;
  comment?: string | null;
}
