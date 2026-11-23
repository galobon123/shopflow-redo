import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input, output } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
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
 * With `buttons`, the same look but plain buttons that emit `pageChange`: for a list inside a page (the reviews),
 * where every page in the history would make "Volver" step through them.
 */
@Component({
  selector: 'sf-pagination',
  imports: [NgTemplateOutlet, RouterLink, LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="sf-pagination" [class.sf-pagination--sm]="size() === 'sm'" [attr.aria-label]="label()">
      <ng-container *ngTemplateOutlet="control; context: { $implicit: page() - 1, off: page() <= 1, label: 'Página anterior', body: prevBody }" />
      @for (item of items(); track $index) {
        @if (item === null) {
          <span aria-hidden="true">…</span>
        } @else {
          <ng-container *ngTemplateOutlet="control; context: { $implicit: item, current: item === page(), label: 'Página ' + item, body: numberBody }" />
        }
      }
      <ng-container *ngTemplateOutlet="control; context: { $implicit: page() + 1, off: page() >= totalPages(), label: 'Página siguiente', body: nextBody }" />
    </nav>

    <!-- A link or a button to one page. Disabled ends are aria-disabled: focus stays on them instead of dropping to the page. -->
    <ng-template #control let-target let-off="off" let-current="current" let-label="label" let-body="body">
      @if (buttons()) {
        <button type="button" [attr.aria-label]="label" [attr.aria-current]="current ? 'page' : null"
          [attr.aria-disabled]="off ? true : null" [attr.tabindex]="off ? -1 : null" (click)="go(target)">
          <ng-container *ngTemplateOutlet="body; context: { $implicit: target }" />
        </button>
      } @else {
        <a [routerLink]="[]" [queryParams]="{ page: param(target) }" queryParamsHandling="merge" [attr.aria-label]="label"
          [attr.aria-current]="current ? 'page' : null" [attr.aria-disabled]="off ? true : null" [attr.tabindex]="off ? -1 : null">
          <ng-container *ngTemplateOutlet="body; context: { $implicit: target }" />
        </a>
      }
    </ng-template>
    <ng-template #prevBody><lucide-icon [img]="prev" [size]="16" /><span class="max-sm:hidden">Anterior</span></ng-template>
    <ng-template #nextBody><span class="max-sm:hidden">Siguiente</span><lucide-icon [img]="next" [size]="16" /></ng-template>
    <ng-template #numberBody let-n>{{ n }}</ng-template>
  `,
})
export class SfPagination {
  /** Current page, starting at 1. */
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly size = input<'md' | 'sm'>('md');
  readonly label = input('Páginas');
  /** Buttons that emit `pageChange` instead of links that change the URL. */
  readonly buttons = input(false, { transform: booleanAttribute });
  /** With `buttons`: the page picked, starting at 1. */
  readonly pageChange = output<number>();

  protected readonly items = computed(() => pageItems(this.page(), this.totalPages()));
  protected readonly prev = ChevronLeft;
  protected readonly next = ChevronRight;

  protected param(page: number): number | null {
    return page <= 1 ? null : page;
  }

  protected go(page: number) {
    if (page >= 1 && page <= this.totalPages() && page !== this.page()) this.pageChange.emit(page);
  }
}
