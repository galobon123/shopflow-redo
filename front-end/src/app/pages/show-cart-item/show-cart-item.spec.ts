import { testProviders, aCartItem } from '../../testing/test-providers';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CartItem } from '../../core/models/cart.model';
import { EcommerceStore } from '../../ecommerce-store';

import { ShowCartItem } from './show-cart-item';

describe('ShowCartItem', () => {
  let fixture: ComponentFixture<ShowCartItem>;

  async function render(item: CartItem) {
    await TestBed.configureTestingModule({
      imports: [ShowCartItem],
      providers: testProviders()
    })
    .compileComponents();

    fixture = TestBed.createComponent(ShowCartItem);
    fixture.componentRef.setInput('item', item);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  /** HU-13 · The selector stops at the stock. */
  it('turns "+" off when the quantity reaches the stock', async () => {
    const el = await render(aCartItem({ quantity: 5, stock: 5 }));

    expect(el.querySelector('[aria-label="Sumar uno"]')!.getAttribute('aria-disabled')).toBe('true');
  });

  it('leaves "+" on below the stock', async () => {
    const el = await render(aCartItem({ quantity: 1, stock: 5 }));

    expect(el.querySelector('[aria-label="Sumar uno"]')!.getAttribute('aria-disabled')).toBeNull();
  });

  /** HU-13 · Escenario: Un producto se desactiva mientras está en el carrito. */
  it('shows an unavailable product dimmed as "No disponible", without the selector', async () => {
    const el = await render(aCartItem({ productName: 'Gorra roja', available: false }));

    expect(el.textContent).toContain('Gorra roja');
    expect(el.textContent).toContain('No disponible');
    expect(el.querySelector('sf-qty-stepper')).toBeNull();
    expect(el.querySelector('.opacity-45')).not.toBeNull();
  });

  it('names its icon buttons after the product and writes prices in pesos', async () => {
    const el = await render(aCartItem({ productName: 'Taza', productPrice: 7630, quantity: 2 }));

    expect(el.querySelector('[aria-label="Quitar Taza del carrito"]')).not.toBeNull();
    expect(el.querySelector('[aria-label="Mover Taza a favoritos"]')).not.toBeNull();
    expect(el.querySelectorAll('mat-icon[aria-hidden="true"]')).toHaveLength(2);
    expect(el.textContent!.replace(/\s/g, ' ')).toContain('$ 7.630');
    expect(el.textContent!.replace(/\s/g, ' ')).toContain('$ 15.260');
  });
});
