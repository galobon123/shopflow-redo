import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ChevronLeft, ChevronRight, LucideAngularModule } from 'lucide-angular';

/** Pages to show: always the first and last, the current one and its neighbours; `null` is a gap ("…"). */
export function pageItems(page: number, totalPages: number): (number | null)[] {
  const pages = new Set([1, totalPages, page - 1, page, page + 1].filter((p) => p >= 1 && p <= totalPages));
  const sorted = [...pages].sort((a, b) => a - b);
  const items: (number | null)[] = [];
  sorted.forEach((p, i) => {
    if (i > 0 && p - sorted[i - 1] > 1) items.push(null);
    items.push(p);
  });
  return items;
}

/**
 * Pages as links (`?page=3`), so every page has a URL that can be shared or opened in a new tab.
 * Keeps the other query params (search, sort); page 1 drops the param.
 */
@Component({
  selector: 'sf-pagination',
  imports: [RouterLink, LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="sf-pagination" aria-label="Páginas">
      <a [routerLink]="[]" [queryParams]="{ page: param(page() - 1) }" queryParamsHandling="merge" aria-label="Página anterior"
        [attr.aria-disabled]="page() <= 1 ? true : null" [attr.tabindex]="page() <= 1 ? -1 : null">
        <lucide-icon [img]="prev" [size]="16" /><span class="max-sm:hidden">Anterior</span>
      </a>
      @for (item of items(); track $index) {
        @if (item === null) {
          <span aria-hidden="true">…</span>
        } @else {
          <a [routerLink]="[]" [queryParams]="{ page: param(item) }" queryParamsHandling="merge"
            [attr.aria-current]="item === page() ? 'page' : null" [attr.aria-label]="'Página ' + item">{{ item }}</a>
        }
      }
      <a [routerLink]="[]" [queryParams]="{ page: param(page() + 1) }" queryParamsHandling="merge" aria-label="Página siguiente"
        [attr.aria-disabled]="page() >= totalPages() ? true : null" [attr.tabindex]="page() >= totalPages() ? -1 : null">
        <span class="max-sm:hidden">Siguiente</span><lucide-icon [img]="next" [size]="16" />
      </a>
    </nav>
  `,
})
export class SfPagination {
  /** Current page, starting at 1. */
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();

  protected readonly items = computed(() => pageItems(this.page(), this.totalPages()));
  protected readonly prev = ChevronLeft;
  protected readonly next = ChevronRight;

  protected param(page: number): number | null {
    return page <= 1 ? null : page;
  }
}
