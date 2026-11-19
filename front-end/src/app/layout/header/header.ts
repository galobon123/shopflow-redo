import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter, map } from 'rxjs';
import { LucideAngularModule, Search } from 'lucide-angular';
import { HeaderActions } from '../header-actions/header-actions';

/** Top bar (UI Kit › Header): brand, search, favourites, cart and profile. */
@Component({
  selector: 'app-header',
  imports: [HeaderActions, RouterLink, LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './header.html',
})
export class Header {
  private readonly router = inject(Router);
  protected readonly searchIcon = Search;

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => this.router.parseUrl(e.urlAfterRedirects)),
    ),
  );

  /** The search box shows what the catalog is showing (?q=…), also after back/forward. */
  protected readonly currentQuery = computed(() => this.url()?.queryParams['q'] ?? '');

  /** HU-10 · Searches inside the category being browsed, or in everything from any other page. */
  search(event: Event, text: string) {
    event.preventDefault();
    const segments = this.url()?.root.children['primary']?.segments ?? [];
    const category = segments[0]?.path === 'products' && segments[1] ? segments[1].path : 'all';
    this.router.navigate(['/products', category], { queryParams: { q: text.trim() || null } });
  }
}
