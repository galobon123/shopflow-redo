import {
  ChangeDetectionStrategy, Component, Injector, afterNextRender, computed, effect, inject, input, signal,
} from '@angular/core';
import { ViewportScroller } from '@angular/common';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink, Scroll } from '@angular/router';
import { PackageOpen, SearchX } from 'lucide-angular';
import { ProductCard } from '../../components/product-card/product-card';
import { ToggleWishlistButton } from '../../components/toggle-wishlist-button/toggle-wishlist-button';
import { EcommerceStore } from '../../ecommerce-store';
import { ProductService, ProductSort } from '../../core/services/product.service';
import { errorMessage } from '../../core/util/error-message';
import { SfButton, SfEmptyState, SfErrorState, SfField, SfInput, SfPagination, SfSkeleton } from '../../ui';

const PAGE_SIZE = 12;
const SORTS: { value: ProductSort; label: string }[] = [
  { value: 'newest', label: 'Novedades' },
  { value: 'price_asc', label: 'Menor precio' },
  { value: 'price_desc', label: 'Mayor precio' },
  { value: 'name', label: 'Nombre (A-Z)' },
];

/**
 * HU-10 · Catalog. Everything that defines the list lives in the URL, so a link can be shared:
 * /products/Remeras?q=lino&sort=price_asc&page=2 (bound to the inputs by withComponentInputBinding).
 */
@Component({
  selector: 'app-products-grid',
  imports: [
    RouterLink, ProductCard, ToggleWishlistButton,
    SfButton, SfEmptyState, SfErrorState, SfField, SfInput, SfPagination, SfSkeleton,
  ],
  templateUrl: './products-grid.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductsGrid {
  readonly store = inject(EcommerceStore);
  private readonly router = inject(Router);
  private readonly productService = inject(ProductService);

  /** Path param: a category name, or "all". */
  readonly categoryName = input<string>('all');
  readonly q = input<string>();
  readonly sort = input<string>();
  /** 1-based in the URL; the API counts from 0. */
  readonly page = input<string>();

  protected readonly sorts = SORTS;
  protected readonly icons = { noResults: SearchX, empty: PackageOpen };
  protected readonly skeletons = Array.from({ length: 8 }, (_, i) => i);

  protected readonly category = computed(() => (this.categoryName() === 'all' ? null : this.categoryName()));
  protected readonly search = computed(() => this.q()?.trim() || null);
  protected readonly sortValue = computed<ProductSort>(
    () => SORTS.find((s) => s.value === this.sort())?.value ?? 'newest',
  );
  protected readonly pageNumber = computed(() => Math.max(1, Number.parseInt(this.page() ?? '1', 10) || 1));

  protected readonly results = rxResource({
    params: () => ({
      category: this.category(),
      q: this.search(),
      sort: this.sortValue(),
      page: this.pageNumber() - 1,
      size: PAGE_SIZE,
    }),
    stream: ({ params }) => this.productService.search(params),
  });

  protected readonly title = computed(() => {
    const q = this.search();
    if (q) return `Resultados para “${q}”`;
    return this.category() ?? 'Todos los productos';
  });

  /**
   * HU-11 · "Atrás" from a product: the router restores the scroll while the list is still a (shorter) skeleton,
   * so it falls short. The position it saved is applied again once the list is on screen.
   */
  private readonly restoreTo = signal<[number, number] | null>(null);

  constructor() {
    const scroller = inject(ViewportScroller);
    const injector = inject(Injector);
    this.router.events.pipe(takeUntilDestroyed()).subscribe((event) => {
      if (event instanceof Scroll) this.restoreTo.set(event.position);
    });
    effect(() => {
      const position = this.restoreTo();
      if (!position || this.results.isLoading()) return;
      this.restoreTo.set(null);
      if (this.results.hasValue()) afterNextRender(() => scroller.scrollToPosition(position), { injector });
    });
  }

  protected readonly count = computed(() => {
    const total = this.results.value()?.totalItems ?? 0;
    return total === 1 ? '1 producto' : `${total} productos`;
  });

  protected readonly error = computed(() => (this.results.error() ? errorMessage(this.results.error()) : null));

  /** Keeps the search and the category; goes back to page 1. */
  changeSort(event: Event) {
    const sort = (event.target as HTMLSelectElement).value;
    this.router.navigate([], {
      queryParams: { sort: sort === 'newest' ? null : sort, page: null },
      queryParamsHandling: 'merge',
    });
  }

  clearSearch() {
    this.router.navigate([], { queryParams: { q: null, page: null }, queryParamsHandling: 'merge' });
  }
}
