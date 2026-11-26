import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { Title } from '@angular/platform-browser';
import { OrderDetail } from './order-detail';
import { anOrder, anOrderItem, testProviders } from '../../testing/test-providers';
import { environment } from '../../../environments/environment';
import { PaymentService } from '../../core/services/payment.service';

const api = environment.apiUrl;
/** Text with every space as a plain one (prices and dates use non-breaking ones). */
const text = (el: Element | null) => el?.textContent?.replace(/\s+/g, ' ') ?? '';

/** HU-18 · The browser shows the page again: from its back/forward cache (`persisted`) or loaded anew. */
function pageshow(persisted: boolean) {
  window.dispatchEvent(Object.assign(new Event('pageshow'), { persisted }));
}

describe('OrderDetail (HU-19)', () => {
  function render(id: string) {
    TestBed.configureTestingModule({ imports: [OrderDetail], providers: testProviders() });
    const fixture = TestBed.createComponent(OrderDetail);
    fixture.componentRef.setInput('id', id);
    fixture.detectChanges();
    const backend = TestBed.inject(HttpTestingController);
    const el = fixture.nativeElement as HTMLElement;
    const update = async () => {
      await fixture.whenStable();
      fixture.detectChanges();
      return el;
    };
    const links = (text: string) => [...el.querySelectorAll('a')].filter((a) => a.textContent?.trim().startsWith(text));
    return { backend, el, update, links };
  }

  it('shows the order: date, status, products, total and address', async () => {
    const { backend, el, update } = render('7');

    expect(el.querySelector('[aria-label="Cargando pedido"]')).not.toBeNull();
    backend.expectOne(`${api}/orders/7`).flush(anOrder());
    await update();

    expect(el.querySelector('h1')?.textContent).toContain('Pedido #7');
    expect(el.textContent).toContain('31 de octubre de 2026');
    expect(el.querySelector('[data-order-heading] sf-badge')?.textContent).toContain('Esperando el pago');
    const items = [...el.querySelectorAll('[data-item]')];
    expect(items).toHaveLength(2);
    expect(text(items[0])).toContain('2 × $ 48.900');
    expect(text(items[0])).toContain('$ 97.800');
    expect(items[0].querySelector('a')?.getAttribute('href')).toBe('/product/15');
    expect(items[0].querySelector('img')?.getAttribute('src')).toBe('https://img.example.com/remera.jpg');
    expect(text(el.querySelector('.sf-summary__total'))).toContain('$ 146.700');
    expect(el.textContent).toContain('San Martín 123');
    expect(el.textContent).toContain('Rosario, Santa Fe');
    expect(TestBed.inject(Title).getTitle()).toBe('Pedido #7 · ShopFlow');
    // The site header is the only banner landmark: the page's title block is not another one.
    expect(el.querySelector('header')).toBeNull();
  });

  it('while it waits for the payment, says until when it can be paid (Argentina time)', async () => {
    const { backend, update } = render('7');

    backend.expectOne(`${api}/orders/7`).flush(anOrder());
    const el = await update();

    expect(text(el.querySelector('sf-alert'))).toContain('Podés pagarlo hasta el 1 de noviembre a las 00:00.');
  });

  it('once the time to pay is up, says the order is going to be cancelled', async () => {
    const { backend, update } = render('7');

    backend.expectOne(`${api}/orders/7`).flush(anOrder({ expiresAt: '2026-11-01T03:00:00Z' }));
    const el = await update();

    expect(text(el.querySelector('sf-alert'))).toContain('El plazo para pagarlo venció el 1 de noviembre a las 00:00');
  });

  it('a paid order has no payment notice', async () => {
    const { backend, update } = render('7');

    backend.expectOne(`${api}/orders/7`).flush(anOrder({ status: 'PAGADA' }));
    const el = await update();

    expect(el.querySelector('sf-alert')).toBeNull();
    expect(el.querySelector('[data-order-heading] sf-badge')?.textContent).toContain('Pagado');
  });

  /** HU-18 · Also where the checkout leaves the order when Mercado Pago couldn't be opened. */
  it('while it can be paid, "Pagar con Mercado Pago" takes the customer to the order\'s checkout', async () => {
    const { backend, update } = render('7');
    const goTo = vi.spyOn(TestBed.inject(PaymentService), 'goTo').mockImplementation(() => {});
    backend.expectOne(`${api}/orders/7`).flush(anOrder());
    const el = await update();

    const pay = [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Pagar con Mercado Pago'))!;
    pay.focus();
    pay.click();
    await update();
    expect(pay.getAttribute('aria-busy')).toBe('true');
    expect(pay.disabled).toBe(false); // busy, not disabled: it keeps the focus
    expect(document.activeElement).toBe(pay);
    pay.click(); // ignored while it waits
    backend.expectOne({ method: 'POST', url: `${api}/orders/7/payment` }).flush({ checkoutUrl: 'https://mp.example/7' });

    expect(goTo).toHaveBeenCalledWith('https://mp.example/7');
  });

  /** Back from Mercado Pago with Atrás: the button lets go, keeps the focus and the order is read again. */
  it('back from Mercado Pago, the button stops waiting and the order is read again', async () => {
    const { backend, update } = render('7');
    vi.spyOn(TestBed.inject(PaymentService), 'goTo').mockImplementation(() => {});
    backend.expectOne(`${api}/orders/7`).flush(anOrder());
    const el = await update();
    const pay = [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Pagar con Mercado Pago'))!;
    pay.focus();
    pay.click();
    backend.expectOne(`${api}/orders/7/payment`).flush({ checkoutUrl: 'https://mp.example/7' });
    await update();
    expect(pay.getAttribute('aria-busy')).toBe('true');

    pageshow(true);
    TestBed.tick(); // not whenStable: the order read again is still unanswered

    expect(pay.getAttribute('aria-busy')).toBeNull();
    backend.expectOne(`${api}/orders/7`).flush(anOrder());
    await update();
    const again = [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Pagar con Mercado Pago'))!;
    expect(again).toBe(pay); // the same button, still focused: the order stayed on screen while read again
    expect(document.activeElement).toBe(pay);

    pay.click(); // the same order's checkout, no new order
    backend.expectOne({ method: 'POST', url: `${api}/orders/7/payment` }).flush({ checkoutUrl: 'https://mp.example/7' });
    backend.expectNone({ method: 'POST', url: `${api}/orders` });
  });

  it('when paying is not available, says why under the button and lets it be tried again', async () => {
    const { backend, update } = render('7');
    backend.expectOne(`${api}/orders/7`).flush(anOrder());
    const el = await update();

    const pay = [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Pagar con Mercado Pago'))!;
    pay.focus();
    pay.click();
    backend.expectOne(`${api}/orders/7/payment`).flush(
      { status: 503, detail: 'El pago no está disponible en este momento. Probá de nuevo más tarde.' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    await update();

    expect(el.querySelector('app-pay-button sf-alert[role="alert"]')?.textContent)
      .toContain('El pago no está disponible en este momento.');
    expect(pay.getAttribute('aria-busy')).toBeNull();
    expect(document.activeElement).toBe(pay);
  });

  /** A 409: it was paid or ran out of time meanwhile. The order is read again and shows why. */
  it('when the order can no longer be paid, reads it again and puts the focus on its title', async () => {
    const { backend, update } = render('7');
    backend.expectOne(`${api}/orders/7`).flush(anOrder());
    const el = await update();

    const pay = [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Pagar con Mercado Pago'))!;
    pay.focus();
    pay.click();
    backend.expectOne(`${api}/orders/7/payment`).flush(
      { status: 409, detail: 'Este pedido ya no está esperando el pago.' }, { status: 409, statusText: 'Conflict' },
    );
    TestBed.tick();
    backend.expectOne(`${api}/orders/7`).flush(anOrder({ status: 'PAGADA' }));
    await update();

    expect(el.querySelector('[data-order-heading] sf-badge')?.textContent).toContain('Pagado');
    expect(el.textContent).not.toContain('Pagar con Mercado Pago');
    expect(document.activeElement).toBe(el.querySelector('h1'));
  });

  it('offers no payment once the time is up or it is paid', async () => {
    const { backend, update } = render('7');
    backend.expectOne(`${api}/orders/7`).flush(anOrder({ expiresAt: '2026-11-01T03:00:00Z' }));
    const el = await update();

    expect(el.querySelector('app-pay-button')).toBeNull();
  });

  it('offers "Calificar" only for the delivered products not reviewed yet, to the reviews of the product', async () => {
    const { backend, update, links } = render('7');

    backend.expectOne(`${api}/orders/7`).flush(anOrder({
      status: 'ENTREGADA',
      items: [anOrderItem({ canReview: true }), anOrderItem({ productId: 16, productName: 'Pantalón', canReview: false })],
    }));
    await update();

    const rate = links('Calificar');
    expect(rate).toHaveLength(1);
    expect(rate[0].getAttribute('href')).toBe('/product/15#reviews');
    expect(rate[0].textContent).toContain('Remera lino');
  });

  /** Escenario: Pedido de otro cliente. The API answers 404, the same as for a missing one. */
  it('someone else\'s order reads "Pedido no encontrado"', async () => {
    const { backend, update, links } = render('77');

    backend.expectOne(`${api}/orders/77`).flush(
      { status: 404, detail: 'Pedido no encontrado.' }, { status: 404, statusText: 'Not Found' },
    );
    const el = await update();

    expect(el.textContent).toContain('Pedido no encontrado');
    expect(links('Ver mis pedidos')[0]?.getAttribute('href')).toBe('/account/orders');
  });

  it('an id that is not a number is not found without asking', async () => {
    const { backend, update } = render('abc');

    const el = await update();

    expect(el.textContent).toContain('Pedido no encontrado');
    backend.expectNone(() => true);
  });

  it('on another error, shows it and tries again', async () => {
    const { backend, update } = render('7');

    backend.expectOne(`${api}/orders/7`).flush({}, { status: 500, statusText: 'Server Error' });
    const el = await update();
    expect(el.textContent).not.toContain('Pedido no encontrado');

    (el.querySelector('sf-error-state button') as HTMLButtonElement).click();
    TestBed.tick();
    backend.expectOne(`${api}/orders/7`).flush(anOrder());
    await update();
    expect(el.querySelector('h1')?.textContent).toContain('Pedido #7');
  });
});
