import { TestBed } from '@angular/core/testing';
import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { ViewportScroller } from '@angular/common';
import { NavigationEnd, Event as RouterEvent, Router, Scroll } from '@angular/router';
import { Subject } from 'rxjs';
import { patchState } from '@ngrx/signals';
import { unprotected } from '@ngrx/signals/testing';
import { EcommerceStore } from '../../ecommerce-store';
import { RecommendationsResponse } from '../../core/models/recommendation.model';
import { ProductsGrid } from './products-grid';
import { aProduct, testProviders } from '../../testing/test-providers';
import { environment } from '../../../environments/environment';
import { ProductResponse } from '../../core/models/product.model';
import { Page } from '../../core/models/page.model';

const api = environment.apiUrl;

function page(items: ProductResponse[], totalItems = items.length, number = 0): Page<ProductResponse> {
  return { items, page: number, size: 12, totalItems, totalPages: Math.ceil(totalItems / 12) };
}

describe('ProductsGrid (HU-10)', () => {
  function render(inputs: { categoryName?: string; q?: string; sort?: string; page?: string } = {}) {
    localStorage.clear();
    TestBed.configureTestingModule({ imports: [ProductsGrid], providers: testProviders() });
    const fixture = TestBed.createComponent(ProductsGrid);
    for (const [name, value] of Object.entries(inputs)) fixture.componentRef.setInput(name, value);
    fixture.detectChanges();
    const backend = TestBed.inject(HttpTestingController);
    backend.match(`${api}/categories`).forEach((r) => r.flush([{ id: 1, name: 'Remeras' }]));
    const el = fixture.nativeElement as HTMLElement;
    const catalogRequest = (): TestRequest => backend.expectOne((r) => r.url === `${api}/products`);
    /** The resource settles asynchronously after the response. */
    const update = async () => {
      await fixture.whenStable();
      fixture.detectChanges();
      return el;
    };
    return { fixture, backend, catalogRequest, update };
  }

  it('asks the API for the URL\'s category, search, sort and page', () => {
    const { catalogRequest } = render({ categoryName: 'Remeras', q: 'lino', sort: 'price_asc', page: '2' });

    const params = catalogRequest().request.params;
    expect(params.get('category')).toBe('Remeras');
    expect(params.get('q')).toBe('lino');
    expect(params.get('sort')).toBe('price_asc');
    expect(params.get('page')).toBe('1'); // the URL counts from 1, the API from 0
    expect(params.get('size')).toBe('12');
  });

  it('leaves "all" and empty filters out of the request', () => {
    const { catalogRequest } = render({ categoryName: 'all' });

    const params = catalogRequest().request.params;
    expect(params.has('category')).toBe(false);
    expect(params.has('q')).toBe(false);
    expect(params.get('sort')).toBe('newest');
  });

  it('shows the products, how many there are and the pages', async () => {
    const { catalogRequest, update } = render({ categoryName: 'Remeras' });

    catalogRequest().flush(page([aProduct({ id: 1, name: 'Remera lino' }), aProduct({ id: 2, name: 'Remera algodón' })], 30));
    const el = await update();

    expect(el.querySelector('h1')?.textContent).toContain('Remeras');
    expect(el.textContent).toContain('30 productos');
    expect(el.querySelectorAll('app-product-card')).toHaveLength(2);
    expect(el.querySelector('sf-pagination [aria-current="page"]')?.textContent?.trim()).toBe('1');
  });

  it('explains an empty search and offers to clear it', async () => {
    const { catalogRequest, update } = render({ q: 'zzzz' });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    catalogRequest().flush(page([]));
    const el = await update();

    expect(el.textContent).toContain("No encontramos productos para 'zzzz'");
    const clear = [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Limpiar búsqueda'))!;
    clear.click();
    expect(navigate).toHaveBeenCalledWith([], { queryParams: { q: null, page: null }, queryParamsHandling: 'merge' });
  });

  it('shows the error and retries', async () => {
    const { catalogRequest, backend, update } = render();

    catalogRequest().error(new ProgressEvent('error'), { status: 0 });
    const el = await update();
    expect(el.textContent).toContain('No pudimos conectar con el servidor');

    const retry = el.querySelector<HTMLButtonElement>('sf-error-state button')!;
    retry.click();
    TestBed.tick(); // starts the new request; it stays pending until answered
    backend.expectOne((r) => r.url === `${api}/products`);
  });

  describe('recommendations (HU-15)', () => {
    /** What GET /recommendations brought in after login (EcommerceStore.loadRecommendations). */
    function recommendations(recs: RecommendationsResponse) {
      patchState(unprotected(TestBed.inject(EcommerceStore)), { recommendations: recs });
    }
    const titles = (el: HTMLElement) => [...el.querySelectorAll('h2.text-title')].map((h) => h.textContent?.trim());

    it('a customer without history sees "Más vendidos" instead of personalized blocks', async () => {
      const { catalogRequest, update } = render();
      catalogRequest().flush(page([aProduct({ id: 1 })]));
      recommendations({ sections: [{ type: 'BEST_SELLERS', products: [aProduct({ id: 20, name: 'Taza' })] }] });
      const el = await update();

      expect(titles(el)).toEqual(['Más vendidos']);
      expect(el.querySelector('[aria-labelledby="recs-BEST_SELLERS"]')?.textContent).toContain('Taza');
    });

    it('a customer with history sees "Recomendados para vos" and "Por tus favoritos", in the order the API sends', async () => {
      const { catalogRequest, update } = render();
      catalogRequest().flush(page([aProduct({ id: 1 })]));
      recommendations({
        sections: [
          { type: 'FOR_YOU', products: [aProduct({ id: 20, name: 'Plato' })] },
          { type: 'WISHLIST', products: [aProduct({ id: 21, name: 'Termo' }), aProduct({ id: 22, name: 'Mate' })] },
        ],
      });
      const el = await update();

      expect(titles(el)).toEqual(['Recomendados para vos', 'Por tus favoritos']);
      expect(el.querySelectorAll('[aria-labelledby="recs-WISHLIST"] app-product-card')).toHaveLength(2);
      expect(el.textContent).not.toContain('Más vendidos');
    });

    it('a guest, or a failed request, shows no block', async () => {
      const { catalogRequest, update } = render();
      catalogRequest().flush(page([aProduct({ id: 1 })]));
      const el = await update();

      expect(titles(el)).toEqual([]);
    });
  });

  describe('"Atrás" from a product (HU-11)', () => {
    /** What the router emits after a navigation; position is only set on back/forward. */
    function routerScroll(position: [number, number] | null) {
      const end = new NavigationEnd(2, '/products/all', '/products/all');
      (TestBed.inject(Router).events as Subject<RouterEvent>).next(new Scroll(end, position, null));
    }

    it('goes back to where it was once the list is on screen, not while the skeleton is', async () => {
      const { fixture, catalogRequest, update } = render();
      const scrollTo = vi.spyOn(TestBed.inject(ViewportScroller), 'scrollToPosition').mockImplementation(() => {});

      routerScroll([0, 3101]);
      fixture.detectChanges();
      expect(scrollTo).not.toHaveBeenCalled(); // still loading

      catalogRequest().flush(page([aProduct({ id: 1 }), aProduct({ id: 2 })], 30));
      await update();
      expect(scrollTo).toHaveBeenCalledExactlyOnceWith([0, 3101]);
    });

    it('a new navigation (no saved position) leaves the scroll to the router', async () => {
      const { catalogRequest, update } = render();
      const scrollTo = vi.spyOn(TestBed.inject(ViewportScroller), 'scrollToPosition').mockImplementation(() => {});

      routerScroll(null);
      catalogRequest().flush(page([aProduct()]));
      await update();
      expect(scrollTo).not.toHaveBeenCalled();
    });
  });
});
