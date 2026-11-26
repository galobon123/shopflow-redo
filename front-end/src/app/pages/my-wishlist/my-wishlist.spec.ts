import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { aWishItem, testProviders } from '../../testing/test-providers';
import { AuthStore } from '../../auth-store';
import { Toaster } from '../../core/services/toaster.service';
import { WishListResponse } from '../../core/models/wishlist.model';
import { environment } from '../../../environments/environment';
import { MyWishlist } from './my-wishlist';

const api = environment.apiUrl;

describe('MyWishlist', () => {
  let fixture: ComponentFixture<MyWishlist>;
  let backend: HttpTestingController;
  let toaster: { success: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn>; info: ReturnType<typeof vi.fn> };

  const four = [
    aWishItem({ productId: 1, productName: 'Taza' }),
    aWishItem({ productId: 2, productName: 'Mate' }),
    aWishItem({ productId: 3, productName: 'Bombilla' }),
    aWishItem({ productId: 4, productName: 'Termo' }),
  ];

  /** Signed in; `wishlist` is what GET /api/wishlist answers (null: leaves it loading). */
  function open(wishlist: WishListResponse[] | null | 'error') {
    toaster = { success: vi.fn(), error: vi.fn(), info: vi.fn() };
    TestBed.configureTestingModule({
      imports: [MyWishlist],
      providers: [
        testProviders(),
        { provide: AuthStore, useValue: { isAuthenticated: signal(true), emailVerified: signal(true) } },
        { provide: Toaster, useValue: toaster },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MyWishlist);
    fixture.detectChanges();
    const read = backend.expectOne(`${api}/wishlist`);
    if (wishlist === 'error') {
      read.flush({ status: 503 }, { status: 503, statusText: 'Service Unavailable' });
    } else if (wishlist) {
      read.flush(wishlist);
    }
    backend.match((r) => r.url.endsWith('/cart/merge')).forEach((r) => r.flush({ items: [], notices: [] }));
    backend.match((r) => !r.url.endsWith('/wishlist')).forEach((r) => r.flush([]));
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const button = (el: HTMLElement, text: string) =>
    [...el.querySelectorAll('button')].find((b) => b.textContent!.includes(text));
  const cards = (el: HTMLElement) => el.querySelectorAll('app-product-card');

  it('shows a skeleton while the favourites load', () => {
    const el = open(null);

    expect(el.querySelector('[aria-label="Cargando favoritos"]')).not.toBeNull();
    expect(el.querySelector('h1')?.textContent).toBe('Mis favoritos'); // the grid doesn't jump when it arrives
  });

  it('says why the favourites could not be read, with a retry', () => {
    const el = open('error');

    expect(el.textContent).toContain('Algo falló de nuestro lado.');
    expect(el.querySelector('h1')?.textContent).toBe('Mis favoritos');
    button(el, 'Reintentar')!.click();
    backend.expectOne(`${api}/wishlist`).flush(four);
    fixture.detectChanges();
    expect(cards(el)).toHaveLength(4);
  });

  it('counts one product in singular', () => {
    const el = open([four[0]]);

    expect(el.textContent).toContain('1 producto');
    expect(el.textContent).not.toContain('1 productos');
  });

  it('says there are no favourites yet', () => {
    const el = open([]);

    expect(el.textContent).toContain('Todavía no guardaste nada');
  });

  it('asks before emptying, and empties with one request', () => {
    const el = open(four);

    button(el, 'Vaciar favoritos')!.click();
    fixture.detectChanges();
    backend.expectNone({ method: 'DELETE' });
    expect(el.textContent).toContain('Se quitan los 4 productos.');
    expect(document.activeElement).toBe(button(el, 'Sí, vaciar'));

    button(el, 'Sí, vaciar')!.click();
    backend.expectOne({ method: 'DELETE', url: `${api}/wishlist` }).flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    expect(el.textContent).toContain('Todavía no guardaste nada');
    expect(document.activeElement).toBe(el.querySelector('sf-empty-state h2'));
    expect(toaster.success).toHaveBeenCalledWith('Vaciaste tus favoritos.');
  });

  it('cancelling the confirmation keeps everything', () => {
    const el = open(four);

    button(el, 'Vaciar favoritos')!.click();
    fixture.detectChanges();
    button(el, 'Cancelar')!.click();
    fixture.detectChanges();

    backend.expectNone({ method: 'DELETE' });
    expect(button(el, 'Sí, vaciar')).toBeUndefined();
    expect(cards(el)).toHaveLength(4);
    expect(document.activeElement).toBe(button(el, 'Vaciar favoritos'));
  });

  const trash = (el: HTMLElement, productId: number) =>
    el.querySelector<HTMLButtonElement>(`[data-remove="${productId}"]`)!;

  /** HU-14 · The keyboard focus never falls to the page while the list changes under it. */
  it('after removing one, the focus goes to the next card, and back if the server fails', () => {
    const el = open(four);

    trash(el, 2).focus();
    trash(el, 2).click();
    fixture.detectChanges();
    expect(cards(el)).toHaveLength(3);
    expect(document.activeElement).toBe(trash(el, 3));

    backend.expectOne({ method: 'DELETE', url: `${api}/wishlist/2` })
      .flush({ status: 500 }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(cards(el)).toHaveLength(4);
    expect(document.activeElement).toBe(trash(el, 2));
  });

  it('after removing the last one, the focus goes to the empty state title', () => {
    const el = open([four[0]]);

    trash(el, 1).focus();
    trash(el, 1).click();
    fixture.detectChanges();

    expect(document.activeElement).toBe(el.querySelector('sf-empty-state h2'));
    backend.expectOne({ method: 'DELETE', url: `${api}/wishlist/1` }).flush(null);
  });

  /** HU-14 · Escenario: Falla al vaciar la wishlist. */
  it('keeps showing the 4 favourites and says so when emptying fails', () => {
    const el = open(four);

    button(el, 'Vaciar favoritos')!.click();
    fixture.detectChanges();
    button(el, 'Sí, vaciar')!.click();
    fixture.detectChanges();
    expect(button(el, 'Sí, vaciar')!.getAttribute('aria-disabled')).toBe('true');
    expect(cards(el)).toHaveLength(4);

    backend.expectOne({ method: 'DELETE', url: `${api}/wishlist` })
      .flush({ status: 500, errorId: 'a1b2c3d4-0000' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(cards(el)).toHaveLength(4);
    expect(toaster.error).toHaveBeenCalledWith('No pudimos vaciar tus favoritos, probá de nuevo.');
    expect(button(el, 'Sí, vaciar')!.hasAttribute('aria-disabled')).toBe(false);
    expect(document.activeElement).toBe(button(el, 'Sí, vaciar'));
  });
});
