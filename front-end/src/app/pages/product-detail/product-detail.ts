import { ChangeDetectionStrategy, Component, computed, effect, inject, input, linkedSignal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { rxResource } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { PackageX } from 'lucide-angular';
import { BackButton } from '../../components/back-button/back-button';
import { ProductCard } from '../../components/product-card/product-card';
import { ProductGallery } from '../../components/product-gallery/product-gallery';
import { ProductReviews } from '../../components/product-reviews/product-reviews';
import { ToggleWishlistButton } from '../../components/toggle-wishlist-button/toggle-wishlist-button';
import { EcommerceStore } from '../../ecommerce-store';
import { ProductService } from '../../core/services/product.service';
import { errorMessage } from '../../core/util/error-message';
import {
  ArsPipe, SfButton, SfEmptyState, SfErrorState, SfQtyStepper, SfSkeleton, SfStars, SfStockBadge, formatRating,
} from '../../ui';

/**
 * HU-11 · Product page at /product/:id (bound by withComponentInputBinding): photos, price with VAT,
 * stock, how many to add, cart and favourite. A missing or inactive product is a 404 from the API.
 * HU-30 · Average stars next to the price and the reviews panel; a new review reloads the product for its average.
 */
@Component({
  selector: 'app-product-detail',
  imports: [
    RouterLink, BackButton, ProductCard, ProductGallery, ProductReviews, ToggleWishlistButton,
    ArsPipe, SfButton, SfEmptyState, SfErrorState, SfQtyStepper, SfSkeleton, SfStars, SfStockBadge,
  ],
  templateUrl: './product-detail.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductDetail {
  readonly store = inject(EcommerceStore);
  private readonly productService = inject(ProductService);
  private readonly title = inject(Title);

  /** Path param. */
  readonly id = input.required<string>();

  /** Anything but a positive whole number can't be a product: no request, straight to "no existe". */
  private readonly productId = computed(() => {
    const id = Number(this.id());
    return Number.isInteger(id) && id > 0 ? id : null;
  });

  protected readonly product = rxResource({
    params: () => this.productId() ?? undefined,
    stream: ({ params: id }) => this.productService.getById(id),
  });

  /** The loaded product; undefined while loading or after an error (value() throws then). */
  protected readonly detail = computed(() => (this.product.hasValue() ? this.product.value() : undefined));

  /** Starts at 1 for each product; the stepper never goes past its stock. */
  protected readonly quantity = linkedSignal({ source: () => this.detail()?.id, computation: () => 1 });

  /**
   * HU-15 · "Otros también compraron" for this product, guests included (the API leaves this one, inactive and
   * sold-out ones out). Secondary: while it loads, when it's empty or if it fails the block just isn't there.
   */
  private readonly alsoBought = rxResource({
    params: () => this.productId() ?? undefined,
    stream: ({ params: id }) => this.productService.alsoBought(id),
  });
  protected readonly othersBought = computed(() => (this.alsoBought.hasValue() ? this.alsoBought.value() : []));

  protected readonly notFound = computed(() => {
    const error = this.product.error();
    return this.productId() === null || (error instanceof HttpErrorResponse && error.status === 404);
  });
  protected readonly error = computed(() =>
    this.product.error() && !this.notFound() ? errorMessage(this.product.error()) : null,
  );

  protected readonly icons = { notFound: PackageX };
  protected readonly formatRating = formatRating;

  constructor() {
    effect(() => {
      const name = this.detail()?.name;
      if (name) this.title.setTitle(`${name} · ShopFlow`);
    });
  }

  addToCart() {
    const product = this.detail();
    if (product) this.store.addToCart(product, this.quantity());
  }
}
