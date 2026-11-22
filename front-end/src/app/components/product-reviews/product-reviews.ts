import {
  ChangeDetectionStrategy, Component, ElementRef, computed, inject, input, linkedSignal, output, signal, viewChild,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { AuthStore } from '../../auth-store';
import { ProductReviews as Reviews } from '../../core/models/review.model';
import { ReviewService } from '../../core/services/review.service';
import { errorMessage } from '../../core/util/error-message';
import { ReviewForm } from '../review-form/review-form';
import { SfButton, SfErrorState, SfSkeleton, SfStars, formatRating } from '../../ui';

/** The store's day (createdAt is an instant): the same date for every visitor, and for the tests in CI's UTC. */
const DATE = new Intl.DateTimeFormat('es-AR', {
  day: 'numeric', month: 'long', year: 'numeric', timeZone: 'America/Argentina/Buenos_Aires',
});

/** "1 estrella", "3 estrellas". */
export function starsText(stars: number): string {
  return `${stars} ${stars === 1 ? 'estrella' : 'estrellas'}`;
}

function reviewsText(count: number): string {
  return `${count} ${count === 1 ? 'reseña' : 'reseñas'}`;
}

/**
 * HU-30 · Reviews panel of the product page: average and count per star (each one a filter), the reviews
 * newest first, and "Calificar" for someone who received the product and hasn't reviewed it.
 */
@Component({
  selector: 'app-product-reviews',
  imports: [ReviewForm, SfButton, SfErrorState, SfSkeleton, SfStars],
  templateUrl: './product-reviews.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductReviews {
  private readonly reviewService = inject(ReviewService);
  private readonly auth = inject(AuthStore);

  readonly productId = input.required<number>();
  /** A review was published: the page refreshes the average next to the price. */
  readonly reviewed = output<void>();

  private readonly title = viewChild.required<ElementRef<HTMLElement>>('title');

  /** Stars to filter by; null is every review. */
  protected readonly rating = signal<number | null>(null);
  /** Starts at 0, back to the first page with each filter. */
  protected readonly page = linkedSignal({ source: this.rating, computation: () => 0 });

  protected readonly reviews = rxResource({
    // The session is part of it: canReview is about whoever is signed in.
    params: () => ({ id: this.productId(), rating: this.rating(), page: this.page(), signedIn: this.auth.isAuthenticated() }),
    stream: ({ params }) => this.reviewService.list(params.id, params.rating, params.page),
  });

  /**
   * The last answer, kept while the next filter or page loads: the filters stay on screen (and keep the focus)
   * and only the list is marked busy.
   */
  protected readonly data = linkedSignal<Reviews | undefined, Reviews | undefined>({
    source: () => (this.reviews.hasValue() ? this.reviews.value() : undefined),
    computation: (next, previous) => next ?? previous?.value,
  });
  protected readonly error = computed(() => (this.reviews.error() ? errorMessage(this.reviews.error()) : null));

  protected readonly starRows = [5, 4, 3, 2, 1];
  protected readonly formatRating = formatRating;
  protected readonly starsText = starsText;
  protected readonly reviewsText = reviewsText;

  /** Width of a star's bar: its share of every review. */
  protected share(count: number, total: number): number {
    return total > 0 ? Math.round((count / total) * 100) : 0;
  }

  protected date(createdAt: string): string {
    return DATE.format(new Date(createdAt));
  }

  /** aria-disabled, not disabled, on the ends: a disabled button would drop the keyboard focus to the page. */
  protected goTo(page: number, totalPages: number) {
    if (page >= 0 && page < totalPages) this.page.set(page);
  }

  /**
   * Back to every review, first page, so the new one is on screen. The form goes away with canReview, so the
   * focus moves to the panel's title instead of falling to the page (WCAG 2.4.3).
   */
  protected onSaved() {
    this.title().nativeElement.focus();
    if (this.rating() === null && this.page() === 0) this.reviews.reload();
    else {
      this.rating.set(null);
      this.page.set(0);
    }
    this.reviewed.emit();
  }
}
