import { signal } from '@angular/core';
import { aCartItem, anOrder, testProviders } from '../../testing/test-providers';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { AuthStore } from '../../auth-store';
import { PaymentService } from '../../core/services/payment.service';
import { environment } from '../../../environments/environment';

import { Checkout } from './checkout';

describe('Checkout', () => {
  let component: Checkout;
  let fixture: ComponentFixture<Checkout>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Checkout],
      providers: testProviders()
    })
    .compileComponents();

    fixture = TestBed.createComponent(Checkout);
    component = fixture.componentInstance;
    fixture.detectChanges(); // not whenStable: the cart and address reads stay unanswered here
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

/** HU-13 · A blocked checkout lists what can't be bought and leads back to the cart. */
describe('Checkout with an unavailable product', () => {
  it('shows the line as "No disponible" without an amount, and links to the cart', async () => {
    TestBed.configureTestingModule({
      imports: [Checkout],
      providers: [
        testProviders(),
        { provide: AuthStore, useValue: { isAuthenticated: signal(true), emailVerified: signal(true) } },
      ],
    });
    const backend = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(Checkout);
    fixture.detectChanges();

    const cart = [
      aCartItem({ productId: 15, productName: 'Taza', productPrice: 1000, quantity: 2 }),
      aCartItem({ productId: 16, productName: 'Gorra roja', productPrice: 5000, quantity: 1, available: false }),
    ];
    backend.match((r) => r.url.endsWith('/cart/merge')).forEach((r) => r.flush({ items: cart, notices: [] }));
    backend.match((r) => !r.url.endsWith('/cart')).forEach((r) => r.flush([]));
    backend.match(`${environment.apiUrl}/cart`).forEach((r) => r.flush(cart));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.textContent).toContain('No disponible');
    expect(el.textContent!.replace(/\s/g, ' ')).not.toContain('$ 5.000');
    expect(el.textContent!.replace(/\s/g, ' ')).toContain('$ 2.000');
    expect(el.querySelector('a[sfAlertAction]')!.getAttribute('href')).toBe('/cart');
  });
});

/** The summary's total, with every space as a plain one. */
const total = (el: HTMLElement) => el.querySelector('.sf-summary__total')?.textContent?.replace(/\s+/g, ' ') ?? '';

/** HU-16 · The order goes to the chosen address; a wrong form sends nothing. */
describe('Checkout shipping', () => {
  const api = environment.apiUrl;
  const saved = { id: 7, firstName: 'Martina', lastName: 'Gómez', address: 'Av. Colón 1234', stateId: 14, cityId: 927,
    cityName: 'Córdoba', stateName: 'Córdoba', zipCode: 'X5000ABC' };

  async function open(addresses: unknown[]) {
    TestBed.configureTestingModule({
      imports: [Checkout],
      providers: [
        testProviders(),
        { provide: AuthStore, useValue: { isAuthenticated: signal(true), emailVerified: signal(true) } },
      ],
    });
    const backend = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(Checkout);
    fixture.detectChanges();
    // The store joins the cart from an effect when the session starts: let it run so the request is out.
    TestBed.tick();
    backend.expectOne({ method: 'POST', url: `${api}/cart/merge` }).flush({ items: [aCartItem()], notices: [] });
    backend.expectOne(`${api}/addresses`).flush(addresses);
    backend.match((r) => r.url.endsWith('/states')).forEach((r) => r.flush([{ id: 14, name: 'Córdoba' }]));
    backend.match((r) => !r.url.endsWith('/cart')).forEach((r) => r.flush([]));
    backend.expectOne({ method: 'GET', url: `${api}/cart` }).flush([aCartItem()]);
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const confirm = [...el.querySelectorAll('button')].find((b) => b.textContent!.includes('Pagar con Mercado Pago'))!;
    return { backend, fixture, el, confirm };
  }

  it('is in Spanish', async () => {
    const { el } = await open([]);

    expect(el.querySelector('h1')?.textContent).toBe('Finalizar compra');
    expect(el.textContent).toContain('Volver al carrito');
    expect(el.textContent).toContain('Envío');
    // HU-18 · No card fields here: the payment happens in Mercado Pago.
    expect(el.textContent).toContain('Te llevamos a Mercado Pago para pagar');
    expect(el.querySelector('input[autocomplete^="cc-"]')).toBeNull();
  });

  /** HU-18 · The same click makes the order and opens Mercado Pago; it keeps waiting until the page leaves. */
  it('pays in Mercado Pago right after making the order, waiting all along with the summary as it was', async () => {
    const { backend, fixture, el, confirm } = await open([saved]);
    const goTo = vi.spyOn(TestBed.inject(PaymentService), 'goTo').mockImplementation(() => {});

    confirm.click();
    backend.expectOne({ method: 'POST', url: `${api}/orders` }).flush(anOrder({ id: 31 }));
    fixture.detectChanges();
    expect(confirm.getAttribute('aria-busy')).toBe('true');
    // Not "$ 0" while Mercado Pago opens: the products and total stay until the page leaves.
    expect(total(el)).toContain('$ 97.800');

    backend.expectOne({ method: 'POST', url: `${api}/orders/31/payment` }).flush({ checkoutUrl: 'https://mp.example/31' });
    fixture.detectChanges();

    expect(goTo).toHaveBeenCalledWith('https://mp.example/31');
    expect(confirm.getAttribute('aria-busy')).toBe('true');
    expect(total(el)).toContain('$ 97.800');
  });

  it('confirms to the saved address that comes chosen', async () => {
    const { backend, confirm } = await open([saved]);

    confirm.click();

    const order = backend.expectOne({ method: 'POST', url: `${api}/orders` });
    expect(order.request.body).toEqual({ addressId: 7 });
    backend.expectNone({ method: 'POST', url: `${api}/addresses` });
  });

  /** HU-17 · Escenario: Confirmo dos veces la misma compra (double click). */
  /** HU-18 · Busy, not disabled: a disabled button would drop the focus to the page (WCAG 2.4.3). */
  it('sends the purchase once, with its key, and waits busy, keeping the focus', async () => {
    const { backend, fixture, confirm } = await open([saved]);

    confirm.focus();
    confirm.click();
    fixture.detectChanges();
    confirm.click();

    const order = backend.expectOne({ method: 'POST', url: `${api}/orders` });
    expect(order.request.headers.get('Idempotency-Key')).toBeTruthy();
    expect(confirm.getAttribute('aria-busy')).toBe('true');
    expect(confirm.getAttribute('aria-disabled')).toBe('true');
    expect(confirm.disabled).toBe(false);
    expect(document.activeElement).toBe(confirm);

    order.flush({ status: 503 }, { status: 503, statusText: 'Service Unavailable' });
    fixture.detectChanges();
    expect(confirm.getAttribute('aria-busy')).toBeNull();
    expect(confirm.getAttribute('aria-disabled')).toBeNull();
  });

  it('with a wrong form sends nothing and marks the fields', async () => {
    const { backend, fixture, el, confirm } = await open([]);

    confirm.click();
    fixture.detectChanges();

    backend.expectNone({ method: 'POST', url: `${api}/addresses` });
    backend.expectNone({ method: 'POST', url: `${api}/orders` });
    expect(el.querySelectorAll('[aria-invalid="true"]').length).toBeGreaterThan(0);
    expect(document.activeElement?.getAttribute('formControlName')).toBe('firstName');
  });
});
