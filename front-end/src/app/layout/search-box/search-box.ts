import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, filter, map, of, switchMap } from 'rxjs';
import { ImageOff, LucideAngularModule, Search } from 'lucide-angular';
import { ProductService } from '../../core/services/product.service';
import { ProductSuggestion } from '../../core/models/product.model';
import { ArsPipe } from '../../ui';

/** Wait after the last key before asking the backend. */
export const SUGGEST_DELAY_MS = 250;
/** Suggestions start from this many letters. */
export const SUGGEST_MIN_LENGTH = 3;

interface Suggestions {
  query: string;
  items: ProductSuggestion[];
}

/**
 * Header search (HU-10) with suggestions as you type: from the 3rd letter, a moment after the last key,
 * the 5 closest products show under the box (accents and small typos don't matter, the backend handles it).
 * Arrows move through them, Enter opens the highlighted one, Escape closes the list.
 */
@Component({
  selector: 'app-search-box',
  imports: [LucideAngularModule, ArsPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './search-box.html',
  host: { class: 'sf-header__search' },
})
export class SearchBox {
  private readonly router = inject(Router);
  private readonly products = inject(ProductService);
  protected readonly searchIcon = Search;
  protected readonly noImageIcon = ImageOff;

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => this.router.parseUrl(e.urlAfterRedirects)),
    ),
  );

  /** The search box shows what the catalog is showing (?q=…), also after back/forward. */
  protected readonly currentQuery = computed(() => this.url()?.queryParams['q'] ?? '');

  protected readonly typed = signal('');
  protected readonly open = signal(false);
  /** Highlighted suggestion (keyboard or mouse), -1 for none. */
  protected readonly active = signal(-1);

  private readonly suggestions = toSignal(
    toObservable(this.typed).pipe(
      map((text) => text.trim()),
      debounceTime(SUGGEST_DELAY_MS),
      distinctUntilChanged(),
      switchMap((query) =>
        query.length < SUGGEST_MIN_LENGTH
          ? of<Suggestions>({ query, items: [] })
          : this.products.suggest(query).pipe(
              map((items): Suggestions => ({ query, items })),
              catchError(() => of<Suggestions>({ query, items: [] })),
            ),
      ),
    ),
    { initialValue: { query: '', items: [] } as Suggestions },
  );

  /** Only the answer for what is in the box now, never a late one for older text. */
  protected readonly items = computed(() => {
    const result = this.suggestions();
    return result.query === this.typed().trim() ? result.items : [];
  });

  /** Searched with enough letters and nothing came back. */
  protected readonly nothingFound = computed(() => {
    const result = this.suggestions();
    const query = this.typed().trim();
    return query.length >= SUGGEST_MIN_LENGTH && result.query === query && result.items.length === 0;
  });

  protected readonly expanded = computed(() => this.open() && (this.items().length > 0 || this.nothingFound()));

  onInput(text: string) {
    this.typed.set(text);
    this.active.set(-1);
    this.open.set(true);
  }

  onKeydown(event: KeyboardEvent) {
    const count = this.items().length;
    if (event.key === 'Escape') {
      this.close();
    } else if (event.key === 'ArrowDown' && count > 0) {
      event.preventDefault();
      this.open.set(true);
      this.active.update((i) => (i + 1) % count);
    } else if (event.key === 'ArrowUp' && count > 0) {
      event.preventDefault();
      this.open.set(true);
      this.active.update((i) => (i <= 0 ? count - 1 : i - 1));
    }
  }

  /** Enter: the highlighted suggestion if there is one, otherwise a normal search. */
  submit(event: Event, text: string) {
    event.preventDefault();
    const highlighted = this.expanded() ? this.items()[this.active()] : undefined;
    if (highlighted) {
      this.choose(highlighted);
      return;
    }
    this.close();
    this.search(text);
  }

  /** HU-11 · Opens the chosen product's page. */
  choose(suggestion: ProductSuggestion) {
    this.close();
    this.typed.set(suggestion.name);
    this.router.navigate(['/product', suggestion.id]);
  }

  close() {
    this.open.set(false);
    this.active.set(-1);
  }

  optionId(index: number) {
    return `search-option-${index}`;
  }

  /** HU-10 · Searches inside the category being browsed, or in everything from any other page. */
  private search(text: string) {
    const segments = this.url()?.root.children['primary']?.segments ?? [];
    const category = segments[0]?.path === 'products' && segments[1] ? segments[1].path : 'all';
    this.router.navigate(['/products', category], { queryParams: { q: text.trim() || null } });
  }
}
