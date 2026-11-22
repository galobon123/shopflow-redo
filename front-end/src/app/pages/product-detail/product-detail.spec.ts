import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { Title } from '@angular/platform-browser';
import { patchState } from '@ngrx/signals';
import { unprotected } from '@ngrx/signals/testing';
import { ProductDetail } from './product-detail';
import { EcommerceStore } from '../../ecommerce-store';
import { aProduct, testProviders } from '../../testing/test-providers';
import { environment } from '../../../environments/environment';
import { ProductDetail as Detail } from '../../core/models/product.model';

const api = environment.apiUrl;

function aDetail(overrides: Partial<Detail> = {}): Detail {
  return { ...aProduct(), images: ['/f/1.jpg', '/f/2.jpg'], averageRating: null, reviewCount: 0, ...overrides };
}

describe('ProductDetail (HU-11)', () => {
  function render(id = '15') {
    localStorage.clear();
    TestBed.configureTestingModule({ imports: [ProductDetail], providers: testProviders() });
    const store = TestBed.inject(EcommerceStore);
    const addToCart = vi.spyOn(store, 'addToCart').mockImplementation(() => {});
    const fixture = TestBed.createComponent(ProductDetail);
    fixture.componentRef.setInput('id', id);
    fixture.detectChanges();
    const backend = TestBed.inject(HttpTestingController);
    backend.match(`${api}/categories`).forEach((r) => r.flush([]));
    const el = fixture.nativeElement as HTMLElement;
    /** The resource settles asynchronously after the response. */
    const update = async () => {
      await fixture.whenStable();
      fixture.detectChanges();
      return el;
    };
    const button = (text: string) => [...el.querySelectorAll('button')].find((b) => b.textContent?.includes(text))!;
    /** What GET /recommendations brought in after login (EcommerceStore.loadRecommendations). */
    const othersBought = (products: ReturnType<typeof aProduct>[]) =>
      patchState(unprotected(store), { recommendations: { recommended: [], othersBought: products } });
    return { fixture, backend, el, update, button, addToCart, othersBought };
  }

  it('shows a skeleton while it loads', () => {
    const { el, backend } = render();

    expect(el.querySelector('[aria-label="Cargando producto"]')).not.toBeNull();
    backend.expectOne(`${api}/products/15`);
  });

  it('shows name, price with VAT, description and "Quedan 3"', async () => {
    const { backend, update } = render();

    backend.expectOne(`${api}/products/15`).flush(aDetail({ stock: 3 }));
    const el = await update();

    expect(el.querySelector('h1')?.textContent).toContain('Remera lino');
    expect(el.textContent).toMatch(/\$\s?48\.900/);
    expect(el.textContent).toContain('IVA incluido');
    expect(el.textContent).toContain('Remera de lino natural');
    expect(el.textContent).toContain('Quedan 3');
    expect(el.querySelectorAll('app-product-gallery .sf-gallery__thumb')).toHaveLength(2);
    expect(TestBed.inject(Title).getTitle()).toBe('Remera lino · ShopFlow');
  });

  it('adds the chosen quantity to the cart, never more than the stock', async () => {
    const { backend, update, button, addToCart, fixture } = render();
    backend.expectOne(`${api}/products/15`).flush(aDetail({ stock: 2 }));
    const el = await update();

    const plus = el.querySelector<HTMLButtonElement>('[aria-label="Sumar uno"]')!;
    plus.click();
    fixture.detectChanges();
    expect(plus.getAttribute('aria-disabled')).toBe('true'); // 2 of 2

    button('Agregar al carrito').click();
    expect(addToCart).toHaveBeenCalledWith(expect.objectContaining({ id: 15 }), 2);
  });

  it('without stock there is nothing to add', async () => {
    const { backend, update, button } = render();

    backend.expectOne(`${api}/products/15`).flush(aDetail({ stock: 0 }));
    const el = await update();

    expect(el.textContent).toContain('Sin stock');
    expect(el.querySelector('sf-qty-stepper')).toBeNull();
    expect(button('Sin stock').disabled).toBe(true);
  });

  it('a missing or inactive product shows "no existe" and a link back to the catalog', async () => {
    const { backend, update } = render('9999');

    backend.expectOne(`${api}/products/9999`).flush(
      { status: 404, title: 'No encontrado', detail: 'Este producto no existe o ya no está disponible.' },
      { status: 404, statusText: 'Not Found' },
    );
    const el = await update();

    expect(el.textContent).toContain('Este producto no existe o ya no está disponible');
    expect(el.querySelector('a[href="/products/all"]')?.textContent).toContain('Volver al catálogo');
  });

  it('an id that is not a number is "no existe" without asking the API', async () => {
    const { backend, update } = render('abc');

    const el = await update();
    backend.expectNone((r) => r.url.startsWith(`${api}/products`));
    expect(el.textContent).toContain('Este producto no existe o ya no está disponible');
  });

  it('shows the error and retries', async () => {
    const { backend, update } = render();

    backend.expectOne(`${api}/products/15`).error(new ProgressEvent('error'), { status: 0 });
    const el = await update();
    expect(el.textContent).toContain('No pudimos conectar con el servidor');

    el.querySelector<HTMLButtonElement>('sf-error-state button')!.click();
    TestBed.tick();
    backend.expectOne(`${api}/products/15`);
  });

  it('shows "Otros también compraron" without the product being viewed', async () => {
    const { backend, update, othersBought } = render();
    othersBought([aProduct(), aProduct({ id: 16, name: 'Short denim' }), aProduct({ id: 17, name: 'Gorra' })]);

    backend.expectOne(`${api}/products/15`).flush(aDetail());
    const el = await update();

    const section = el.querySelector('[aria-labelledby="others-title"]');
    expect(section?.textContent).toContain('Otros también compraron');
    expect(section?.querySelectorAll('app-product-card')).toHaveLength(2);
    expect(section?.textContent).not.toContain('Remera lino');
    expect(section?.querySelectorAll('app-toggle-wishlist-button')).toHaveLength(2);
  });

  it('hides "Otros también compraron" when there is nothing else to show', async () => {
    const { backend, update, othersBought } = render();

    backend.expectOne(`${api}/products/15`).flush(aDetail());
    let el = await update();
    expect(el.textContent).not.toContain('Otros también compraron'); // guest, or the request failed

    othersBought([aProduct()]); // only the product being viewed
    el = await update();
    expect(el.textContent).not.toContain('Otros también compraron');
  });
});
