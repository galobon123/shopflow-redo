import {
  ChangeDetectionStrategy, Component, ElementRef, Injector, afterNextRender, computed, effect, inject, input, linkedSignal,
  output, signal, viewChild,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { rxResource } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { AuthStore } from '../../auth-store';
import { ProductReviews as Reviews } from '../../core/models/review.model';
import { ReviewService } from '../../core/services/review.service';
import { errorMessage } from '../../core/util/error-message';
import { ReviewForm } from '../review-form/review-form';
import { ReviewText } from '../review-text/review-text';
import { SfButton, SfErrorState, SfPagination, SfSkeleton, SfStars, formatRating } from '../../ui';

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
 * newest first, and "Calificar" for someone who received the product and hasn't reviewed it. Someone who did
 * sees their own review on top, with "Editar".
 */
@Component({
  selector: 'app-product-reviews',
  imports: [NgTemplateOutlet, ReviewForm, ReviewText, SfButton, SfErrorState, SfPagination, SfSkeleton, SfStars],
  templateUrl: './product-reviews.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductReviews {
  private readonly reviewService = inject(ReviewService);
  private readonly auth = inject(AuthStore);
  private readonly injector = inject(Injector);

  readonly productId = input.required<number>();
  /** A review was published: the page refreshes the average next to the price. */
  readonly reviewed = output<void>();

  private readonly title = viewChild.required<ElementRef<HTMLElement>>('title');
  private readonly editButton = viewChild('editButton', { read: ElementRef<HTMLElement> });

  /** Changing the own review; closed again for another product. */
  protected readonly editing = linkedSignal({ source: this.productId, computation: () => false });

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

  constructor() {
    // HU-19 · "Calificar" in "Mis pedidos" opens /product/:id#reviews. The panel loads after the router's own anchor
    // scroll, so once it is on screen it goes to the top (with the form under its title) and takes the focus.
    if (inject(ActivatedRoute).snapshot.fragment === 'reviews') {
      const jump = effect(() => {
        if (!this.data()) return;
        jump.destroy();
        afterNextRender(() => {
          const title = this.title().nativeElement;
          title.scrollIntoView({ block: 'start' });
          title.focus({ preventScroll: true });
        }, { injector: this.injector });
      });
    }
  }

  /** Width of a star's bar: its share of every review. */
  protected share(count: number, total: number): number {
    return total > 0 ? Math.round((count / total) * 100) : 0;
  }

  protected date(createdAt: string): string {
    return DATE.format(new Date(createdAt));
  }

  /**
   * Back to every review, first page, so the new or changed one is on screen. The form goes away, so the
   * focus moves to the panel's title instead of falling to the page (WCAG 2.4.3).
   */
  protected onSaved() {
    this.editing.set(false);
    this.title().nativeElement.focus();
    if (this.rating() === null && this.page() === 0) this.reviews.reload();
    else {
      this.rating.set(null);
      this.page.set(0);
    }
    this.reviewed.emit();
  }

  /** Back to the review as it was, with the focus on "Editar" again. */
  protected stopEditing() {
    this.editing.set(false);
    afterNextRender(() => this.editButton()?.nativeElement.focus(), { injector: this.injector });
  }
}
