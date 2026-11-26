import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { OrderSuccess } from './order-success';
import { anOrder, testProviders } from '../../testing/test-providers';
import { environment } from '../../../environments/environment';

const api = environment.apiUrl;

describe('OrderSuccess (HU-19)', () => {
  function render(id: string) {
    TestBed.configureTestingModule({ imports: [OrderSuccess], providers: testProviders() });
    const fixture = TestBed.createComponent(OrderSuccess);
    fixture.componentRef.setInput('id', id);
    fixture.detectChanges();
    const backend = TestBed.inject(HttpTestingController);
    const el = fixture.nativeElement as HTMLElement;
    const update = async () => {
      await fixture.whenStable();
      fixture.detectChanges();
      return el;
    };
    return { backend, el, update };
  }

  /** It reads the order by the id in the URL, so a reload shows it again. */
  it('reads the order by its id and shows the receipt, with a link to "Mis pedidos"', async () => {
    const { backend, el, update } = render('7');

    expect(el.querySelector('[aria-label="Cargando pedido"]')).not.toBeNull();
    backend.expectOne(`${api}/orders/7`).flush(anOrder());
    await update();

    expect(el.querySelector('h1')?.textContent).toContain('¡Gracias por tu compra!');
    expect(el.textContent).toContain('Recibimos tu pedido #7');
    expect(el.querySelector('[data-order-heading] sf-badge')?.textContent).toContain('Esperando el pago');
    expect(el.querySelector('sf-alert')?.textContent?.replace(/\s+/g, ' ')).toContain('hasta el 1 de noviembre a las 00:00.');
    expect(el.querySelectorAll('[data-item]')).toHaveLength(2);
    expect(el.querySelector('header')).toBeNull(); // the site header is the only banner landmark
    const toOrders = [...el.querySelectorAll('a')].find((a) => a.textContent?.trim() === 'Ver mis pedidos');
    expect(toOrders?.getAttribute('href')).toBe('/account/orders');
  });

  it('someone else\'s or a missing order reads "Pedido no encontrado"', async () => {
    const { backend, update } = render('77');

    backend.expectOne(`${api}/orders/77`).flush({}, { status: 404, statusText: 'Not Found' });
    const el = await update();

    expect(el.textContent).toContain('Pedido no encontrado');
  });

  it('on another error, offers to try again', async () => {
    const { backend, update } = render('7');

    backend.expectOne(`${api}/orders/7`).flush({}, { status: 503, statusText: 'Service Unavailable' });
    const el = await update();

    expect(el.querySelector('sf-error-state')).not.toBeNull();
  });
});
