import { signal } from '@angular/core';
import { aCartItem, testProviders } from '../../testing/test-providers';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { AuthStore } from '../../auth-store';
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
    await fixture.whenStable();
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
