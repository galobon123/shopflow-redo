import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { MyOrders } from './my-orders';
import { anOrder, anOrderItem, testProviders } from '../../testing/test-providers';
import { environment } from '../../../environments/environment';
import { OrderResponse } from '../../core/models/order.model';
import { Page } from '../../core/models/page.model';

const api = environment.apiUrl;
/** Text with every space as a plain one (prices and dates use non-breaking ones). */
const text = (el: Element) => el.textContent!.replace(/\s+/g, ' ');

function page(items: OrderResponse[], totalItems = items.length, number = 0): Page<OrderResponse> {
  return { items, page: number, size: 10, totalItems, totalPages: Math.ceil(totalItems / 10) };
}

describe('MyOrders (HU-19)', () => {
  function render(pageParam?: string) {
    TestBed.configureTestingModule({ imports: [MyOrders], providers: testProviders() });
    const fixture = TestBed.createComponent(MyOrders);
    if (pageParam) fixture.componentRef.setInput('page', pageParam);
    fixture.detectChanges();
    const backend = TestBed.inject(HttpTestingController);
    const el = fixture.nativeElement as HTMLElement;
    const listRequest = () => backend.expectOne((r) => r.url === `${api}/orders`);
    const update = async () => {
      await fixture.whenStable();
      fixture.detectChanges();
      return el;
    };
    const rows = () => [...el.querySelectorAll('[data-order]')] as HTMLElement[];
    return { backend, el, listRequest, update, rows };
  }

  it('shows a skeleton while it loads', () => {
    const { el, listRequest } = render();

    expect(el.querySelector('[aria-label="Cargando pedidos"]')).not.toBeNull();
    listRequest();
  });

  /** Escenario: Ver mis pedidos. The API sends them newest first; the page keeps that order. */
  it('lists the three orders as they come, each with its date, number, products, total and status', async () => {
    const { listRequest, update, rows } = render();

    listRequest().flush(page([
      anOrder({ id: 9, status: 'PENDIENTE_PAGO' }),
      anOrder({ id: 8, status: 'ENVIADA', createdAt: '2026-10-15T15:00:00Z', total: 48900, items: [anOrderItem({ quantity: 1 })] }),
      anOrder({ id: 7, status: 'ENTREGADA', createdAt: '2026-09-01T12:00:00Z' }),
    ]));
    await update();

    expect(rows()).toHaveLength(3);
    const [newest, middle, oldest] = rows();
    expect(text(newest)).toContain('Pedido #9');
    // 02:30 UTC on November 1st is still October 31st in Argentina.
    expect(text(newest)).toContain('31 de octubre de 2026');
    // "3 productos" never breaks between the number and the word on a narrow screen.
    expect(text(newest.querySelector('.whitespace-nowrap')!)).toBe('3 productos');
    expect(text(newest)).toContain('$ 146.700');
    expect(newest.querySelector('sf-badge')?.textContent).toContain('Esperando el pago');
    expect(newest.querySelector('a')?.getAttribute('href')).toBe('/account/orders/9');
    expect(text(middle)).toContain('1 producto');
    expect(middle.querySelector('sf-badge')?.textContent).toContain('Enviado');
    expect(oldest.querySelector('sf-badge')?.textContent).toContain('Entregado');
  });

  it('asks the API for the page in the URL, counting from 0, and shows the pages', async () => {
    const { listRequest, update } = render('2');

    const request = listRequest();
    expect(request.request.params.get('page')).toBe('1');
    request.flush(page([anOrder()], 11, 1));
    const el = await update();

    const current = el.querySelector('sf-pagination [aria-current="page"]');
    expect(current?.textContent?.trim()).toBe('2');
  });

  it('without orders, says so and links to the catalog', async () => {
    const { listRequest, update } = render();

    listRequest().flush(page([]));
    const el = await update();

    expect(el.textContent).toContain('Todavía no hiciste compras');
    expect(el.querySelector('a[href="/products/all"]')).not.toBeNull();
    expect(el.querySelector('sf-pagination')).toBeNull();
  });

  it('on an error, shows it and tries again', async () => {
    const { listRequest, update, backend } = render();

    listRequest().flush({ detail: 'x' }, { status: 503, statusText: 'Service Unavailable' });
    const el = await update();
    expect(el.querySelector('sf-error-state')).not.toBeNull();

    (el.querySelector('sf-error-state button') as HTMLButtonElement).click();
    TestBed.tick();
    listRequest().flush(page([anOrder()]));
    await update();
    expect(el.querySelector('sf-error-state')).toBeNull();
    backend.verify();
  });
});
