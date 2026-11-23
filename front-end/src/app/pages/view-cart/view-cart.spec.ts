import { signal } from '@angular/core';
import { aCartItem, testProviders } from '../../testing/test-providers';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { AuthStore } from '../../auth-store';
import { CartItem } from '../../core/models/cart.model';
import { environment } from '../../../environments/environment';

import { EcommerceStore } from '../../ecommerce-store';
import { ViewCart } from './view-cart';

const api = environment.apiUrl;

describe('ViewCart', () => {
  let fixture: ComponentFixture<ViewCart>;
  let backend: HttpTestingController;

  function configure() {
    TestBed.configureTestingModule({
      imports: [ViewCart],
      providers: [
        testProviders(),
        { provide: AuthStore, useValue: { isAuthenticated: signal(true), emailVerified: signal(true) } },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
  }

  /** Answers every pending GET /api/cart with `cart` (null: leaves them loading) and the rest with []. */
  function answer(cart: CartItem[] | null | 'error') {
    backend.match((r) => !r.url.endsWith('/cart')).forEach((r) => r.flush([]));
    const requests = backend.match(`${api}/cart`);
    expect(requests.length).toBeGreaterThan(0);
    for (const request of requests) {
      if (cart === 'error') {
        request.flush({ status: 503, detail: 'No pudimos leer tu carrito.' }, { status: 503, statusText: 'Service Unavailable' });
      } else if (cart) {
        request.flush(cart);
      }
    }
  }

  /** Opens the cart page for a signed-in customer; `cart` is what GET /api/cart answers. */
  async function open(cart: CartItem[] | null | 'error') {
    configure();
    fixture = TestBed.createComponent(ViewCart);
    fixture.detectChanges();
    answer(cart);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const checkoutButton = (el: HTMLElement) =>
    [...el.querySelectorAll('button')].find((b) => b.textContent!.includes('Continuar al pago'))!;

  it('shows a skeleton while the cart loads', async () => {
    const el = await open(null);

    expect(el.querySelector('[aria-label="Cargando carrito"]')).not.toBeNull();
  });

  it('says the cart is empty', async () => {
    const el = await open([]);

    expect(el.textContent).toContain('Tu carrito está vacío');
  });

  it('shows the error with a retry', async () => {
    const el = await open('error');

    expect(el.textContent).toContain('Algo falló de nuestro lado.');
    expect(el.textContent).toContain('Reintentar');
  });

  it('lets an available cart continue to checkout', async () => {
    const el = await open([aCartItem({ quantity: 1, stock: 5 })]);

    expect(checkoutButton(el).disabled).toBe(false);
  });

  /** HU-13 · Escenario: Un producto se desactiva mientras está en el carrito (after the cart was read at login). */
  it('reads the cart again on opening the page and shows what changed since', async () => {
    configure();
    const store = TestBed.inject(EcommerceStore);
    TestBed.tick();
    answer([aCartItem({ productId: 16, productName: 'Gorra roja', available: true })]);
    expect(store.cartBlocked()).toBe(false);

    fixture = TestBed.createComponent(ViewCart);
    fixture.detectChanges();
    backend.expectOne({ method: 'GET', url: `${api}/cart` })
      .flush([aCartItem({ productId: 16, productName: 'Gorra roja', available: false })]);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.textContent).toContain('No disponible');
    expect(checkoutButton(el).disabled).toBe(true);
  });

  /** HU-13 · Escenario: Un producto se desactiva mientras está en el carrito. */
  it('blocks the checkout until the unavailable product is taken out', async () => {
    const el = await open([
      aCartItem({ productId: 15 }),
      aCartItem({ productId: 16, productName: 'Gorra roja', available: false }),
    ]);

    expect(el.textContent).toContain('No disponible');
    expect(checkoutButton(el).disabled).toBe(true);

    el.querySelector<HTMLButtonElement>('[aria-label="Quitar Gorra roja del carrito"]')!.click();
    backend.expectOne({ method: 'DELETE', url: `${api}/cart/16` }).flush(null);
    fixture.detectChanges();

    expect(el.textContent).not.toContain('Gorra roja');
    expect(checkoutButton(el).disabled).toBe(false);
  });

  /** HU-13 · WCAG 2.4.3: pressing a button that makes its row go away never drops the focus to the page. */
  describe('focus', () => {
    afterEach(() => fixture?.nativeElement.remove());

    async function openAttached(cart: CartItem[]) {
      const el = await open(cart);
      document.body.appendChild(el);
      return el;
    }

    /** Tab to the button and press Enter (a click on a focused button). */
    function press(el: HTMLElement, selector: string) {
      const button = el.querySelector<HTMLButtonElement>(selector)!;
      button.focus();
      button.click();
    }

    async function settle() {
      fixture.detectChanges();
      await fixture.whenStable();
    }

    it('goes to the "Quitar" of the next row after removing a product', async () => {
      const el = await openAttached([aCartItem({ productId: 15, productName: 'Taza' }), aCartItem({ productId: 16, productName: 'Plato' })]);

      press(el, '[aria-label="Quitar Taza del carrito"]');
      backend.expectOne({ method: 'DELETE', url: `${api}/cart/15` }).flush(null);
      await settle();

      expect(document.activeElement?.getAttribute('aria-label')).toBe('Quitar Plato del carrito');
    });

    it('goes to the "Productos" title after removing the last row', async () => {
      const el = await openAttached([aCartItem({ productId: 15, productName: 'Taza' }), aCartItem({ productId: 16, productName: 'Plato' })]);

      press(el, '[aria-label="Quitar Plato del carrito"]');
      backend.expectOne({ method: 'DELETE', url: `${api}/cart/16` }).flush(null);
      await settle();

      expect(document.activeElement?.textContent?.trim()).toBe('Productos');
    });

    it('goes to the way back to the catalogue when the cart is left empty', async () => {
      const el = await openAttached([aCartItem({ productId: 15, productName: 'Taza' })]);

      press(el, '[aria-label="Quitar Taza del carrito"]');
      backend.expectOne({ method: 'DELETE', url: `${api}/cart/15` }).flush(null);
      await settle();

      expect(document.activeElement?.textContent?.trim()).toBe('Ver el catálogo');
    });

    it('goes to "Restar uno" of the selector after "Dejar N"', async () => {
      const el = await openAttached([aCartItem({ productId: 15, quantity: 4, stock: 2 })]);

      press(el, '[sfAlertAction]');
      backend.expectOne({ method: 'PUT', url: `${api}/cart/15?qty=2` }).flush(null);
      await settle();

      expect(document.activeElement?.getAttribute('aria-label')).toBe('Restar uno');
    });
  });
});
