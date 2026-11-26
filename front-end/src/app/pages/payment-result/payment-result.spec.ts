import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { PaymentResult } from './payment-result';
import { PaymentService } from '../../core/services/payment.service';
import { OrderResponse } from '../../core/models/order.model';
import { anOrder, testProviders } from '../../testing/test-providers';
import { environment } from '../../../environments/environment';

const api = environment.apiUrl;
const text = (el: Element | null) => el?.textContent?.replace(/\s+/g, ' ') ?? '';

/** HU-18 · Back from Mercado Pago: what the order really is, never what the URL says. */
describe('PaymentResult (HU-18)', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  function render(id: string, outcome: string) {
    TestBed.configureTestingModule({ imports: [PaymentResult], providers: testProviders() });
    const goTo = vi.spyOn(TestBed.inject(PaymentService), 'goTo').mockImplementation(() => {});
    const fixture = TestBed.createComponent(PaymentResult);
    fixture.componentRef.setInput('id', id);
    fixture.componentRef.setInput('outcome', outcome);
    fixture.detectChanges();
    const backend = TestBed.inject(HttpTestingController);
    const el = fixture.nativeElement as HTMLElement;
    /** The resource settles on a microtask; the timers stay fake, so nothing is read again until a test says. */
    const update = async () => {
      await vi.advanceTimersByTimeAsync(0);
      fixture.detectChanges();
      return el;
    };
    /** The order as the store has it now, answered to the page's next read. */
    const answer = (order: OrderResponse) => {
      backend.expectOne(`${api}/orders/${id}`).flush(order);
      return update();
    };
    const status = () => text(el.querySelector('[data-payment-status]'));
    return { backend, el, update, answer, status, goTo };
  }

  /** Escenario: Pago aprobado (the webhook got there first). */
  it('an approved payment of a paid order says so, with the receipt', async () => {
    const { el, answer, status, backend } = render('7', 'approved');

    expect(el.querySelector('[aria-label="Cargando pedido"]')).not.toBeNull();
    await answer(anOrder({ status: 'PAGADA' }));

    expect(el.querySelector('h1')?.textContent).toContain('¡Pago aprobado!');
    expect(status()).toContain('Tu pedido #7 está pagado.');
    expect(el.querySelector('[data-payment-status]')?.getAttribute('aria-live')).toBe('polite');
    expect(el.querySelectorAll('[data-item]')).toHaveLength(2);
    await vi.advanceTimersByTimeAsync(PaymentResult.POLL_EVERY_MS * 2);
    backend.expectNone(`${api}/orders/7`);
  });

  /** The webhook is late: the page waits for it, reading the order again every few seconds. */
  it('while the approved payment is not confirmed yet, says so and reads the order again until it is', async () => {
    const { el, answer, status, backend } = render('7', 'approved');

    await answer(anOrder());
    expect(el.querySelector('h1')?.textContent).toContain('Estamos confirmando tu pago…');
    expect(status()).not.toContain('aprobado');
    // The spinner fills the same 40 x 40 box as the icon that replaces it, so nothing moves.
    expect(el.querySelector('[data-status-indicator]')?.classList).toContain('size-10');
    expect(el.querySelector('[data-status-indicator] .sf-spinner')).not.toBeNull();

    await vi.advanceTimersByTimeAsync(PaymentResult.POLL_EVERY_MS - 1);
    backend.expectNone(`${api}/orders/7`);
    await vi.advanceTimersByTimeAsync(1);
    await answer(anOrder());
    expect(el.querySelector('h1')?.textContent).toContain('Estamos confirmando tu pago…');

    await vi.advanceTimersByTimeAsync(PaymentResult.POLL_EVERY_MS);
    await answer(anOrder({ status: 'PAGADA' }));
    expect(el.querySelector('h1')?.textContent).toContain('¡Pago aprobado!');
    expect(el.querySelector('[data-status-indicator]')?.classList).toContain('size-10');

    await vi.advanceTimersByTimeAsync(PaymentResult.POLL_EVERY_MS * 2);
    backend.expectNone(`${api}/orders/7`);
  });

  /** For a limited time: then it says it may take longer, and stops asking. */
  it('stops reading after a while and says the payment is not confirmed yet', async () => {
    const { el, answer, backend } = render('7', 'approved');

    await answer(anOrder());
    for (let read = 2; read <= PaymentResult.POLL_TIMES; read++) {
      await vi.advanceTimersByTimeAsync(PaymentResult.POLL_EVERY_MS);
      await answer(anOrder());
    }

    expect(el.querySelector('h1')?.textContent).toContain('Tu pago todavía no se confirmó');
    await vi.advanceTimersByTimeAsync(PaymentResult.POLL_EVERY_MS * 3);
    backend.expectNone(`${api}/orders/7`);
  });

  /** The URL says "approved", but only the order can say it's paid. */
  it('a made-up "approved" in the URL never shows the order as paid', async () => {
    const { el, answer } = render('7', 'approved');

    await answer(anOrder());

    expect(el.textContent).not.toContain('¡Pago aprobado!');
  });

  /** Escenario: Pago rechazado. "Tu pago fue rechazado", with the option to try again. */
  it('a rejected payment says so and offers to try again, which goes back to Mercado Pago', async () => {
    const { el, answer, backend, status, update, goTo } = render('7', 'rejected');

    await answer(anOrder());

    expect(el.querySelector('h1')?.textContent).toContain('Tu pago fue rechazado');
    expect(status()).toContain('hasta el 1 de noviembre a las 00:00');
    const retry = [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Reintentar el pago'))!;
    retry.click();
    await update();
    backend.expectOne({ method: 'POST', url: `${api}/orders/7/payment` }).flush({ checkoutUrl: 'https://mp.example/7' });
    expect(goTo).toHaveBeenCalledWith('https://mp.example/7');

    await vi.advanceTimersByTimeAsync(PaymentResult.POLL_EVERY_MS * 2);
    backend.expectNone(`${api}/orders/7`); // nothing to wait for
  });

  /** It ran out of time meanwhile (409): the page reads the order again and the focus goes to what it says now. */
  it('a retry refused because the order is no longer payable moves the focus to the new title', async () => {
    const { el, answer, backend, update } = render('7', 'rejected');
    await answer(anOrder());

    const retry = [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Reintentar el pago'))!;
    retry.focus();
    retry.click();
    backend.expectOne(`${api}/orders/7/payment`).flush(
      { status: 409, detail: 'Este pedido ya no está esperando el pago.' }, { status: 409, statusText: 'Conflict' },
    );
    await update();
    await answer(anOrder({ status: 'CANCELADA' }));

    expect(el.querySelector('h1')?.textContent).toContain('Este pedido está cancelado');
    expect(document.activeElement).toBe(el.querySelector('h1'));
  });

  it('a payment Mercado Pago is still processing reads as pending', async () => {
    const { el, answer } = render('7', 'pending');

    await answer(anOrder());

    expect(el.querySelector('h1')?.textContent).toContain('Tu pago está pendiente');
  });

  /** Escenario: vencimiento. Cancelled or out of time: say it and lead back to the catalog. */
  it('a cancelled order says so and leads back to the catalog', async () => {
    const { el, answer, status } = render('7', 'rejected');

    await answer(anOrder({ status: 'CANCELADA' }));

    expect(el.querySelector('h1')?.textContent).toContain('Este pedido está cancelado');
    expect(status()).not.toContain('te devolvemos');
    const back = [...el.querySelectorAll('a')].find((a) => a.textContent?.trim() === 'Volver al catálogo');
    expect(back?.getAttribute('href')).toBe('/products/all');
    expect(el.textContent).not.toContain('Reintentar el pago');
  });

  /** Approved after it was cancelled (the store marks it for review): the customer learns the money comes back. */
  it('an approved payment of a cancelled order says the money is given back', async () => {
    const { answer, status } = render('7', 'approved');

    await answer(anOrder({ status: 'CANCELADA' }));

    expect(status()).toContain('Si Mercado Pago te cobró, lo revisamos y te devolvemos el dinero.');
  });

  it('an order whose time to pay is up says so, without offering to pay', async () => {
    const { el, answer } = render('7', 'rejected');

    await answer(anOrder({ expiresAt: '2026-11-01T03:00:00Z' }));

    expect(el.querySelector('h1')?.textContent).toContain('Se venció el plazo para pagar');
    expect(el.querySelector('app-pay-button')).toBeNull();
  });

  it('someone else\'s or a missing order reads "Pedido no encontrado"', async () => {
    const { backend, update } = render('77', 'approved');

    backend.expectOne(`${api}/orders/77`).flush({}, { status: 404, statusText: 'Not Found' });

    expect((await update()).textContent).toContain('Pedido no encontrado');
  });

  it('on another error, offers to try again', async () => {
    const { backend, update } = render('7', 'approved');

    backend.expectOne(`${api}/orders/7`).flush({}, { status: 503, statusText: 'Service Unavailable' });

    expect((await update()).querySelector('sf-error-state')).not.toBeNull();
  });
});
