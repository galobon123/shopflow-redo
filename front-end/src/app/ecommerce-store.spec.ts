import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { EcommerceStore } from './ecommerce-store';
import { AuthStore } from './auth-store';
import { Toaster } from './core/services/toaster.service';
import { errorMessage } from './core/util/error-message';
import { environment } from '../environments/environment';
import { aCartItem, aProduct, testProviders } from './testing/test-providers';

const api = environment.apiUrl;

describe('EcommerceStore', () => {
  let store: InstanceType<typeof EcommerceStore>;
  let backend: HttpTestingController;
  let toaster: { success: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn>; info: ReturnType<typeof vi.fn> };
  const loggedIn = signal(false);

  function setup(authenticated: boolean, cart = [aCartItem()]) {
    loggedIn.set(authenticated);
    toaster = { success: vi.fn(), error: vi.fn(), info: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        testProviders(),
        { provide: AuthStore, useValue: { isAuthenticated: loggedIn } },
        { provide: Toaster, useValue: toaster },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    store = TestBed.inject(EcommerceStore);
    TestBed.tick();

    // Requests the store makes on start.
    backend.expectOne(`${api}/categories`).flush([]);
    if (authenticated) {
      backend.expectOne(`${api}/wishlist`).flush([]);
      backend.expectOne(`${api}/cart`).flush(cart);
      backend.match((r) => r.url.includes('/recommendations')).forEach((r) => r.flush({ recommended: [], othersBought: [] }));
    }
  }

  afterEach(() => backend.verify());

  it('asks a guest to sign in instead of calling the cart API', () => {
    setup(false);

    store.addToCart(aProduct());

    expect(toaster.info).toHaveBeenCalledWith('Iniciá sesión para agregar productos al carrito.');
    expect(store.cartItems()).toEqual([]);
  });

  it('adds a product and counts units and total', () => {
    setup(true, []);

    store.addToCart(aProduct({ id: 15, price: 1000 }));
    backend.expectOne({ method: 'POST', url: `${api}/cart/15?qty=1` }).flush(null);
    store.addToCart(aProduct({ id: 15, price: 1000 }));
    backend.expectOne({ method: 'POST', url: `${api}/cart/15?qty=1` }).flush(null);

    expect(store.cartItems()).toHaveLength(1);
    expect(store.cartCount()).toBe(2);
    expect(store.cartTotal()).toBe(2000);
  });

  it('adds the quantity chosen on the product page (HU-11)', () => {
    setup(true, []);

    store.addToCart(aProduct({ id: 15, price: 1000 }), 3);
    backend.expectOne({ method: 'POST', url: `${api}/cart/15?qty=3` }).flush(null);
    store.addToCart(aProduct({ id: 15, price: 1000 }), 2);
    backend.expectOne({ method: 'POST', url: `${api}/cart/15?qty=2` }).flush(null);

    expect(store.cartCount()).toBe(5);
    expect(store.cartTotal()).toBe(5000);
  });

  it('shows the backend message when the cart rejects the product (HU-03)', () => {
    setup(true, []);

    store.addToCart(aProduct({ id: 15 }));
    backend.expectOne(`${api}/cart/15?qty=1`).flush(
      { status: 400, title: 'Solicitud inválida', detail: 'No hay stock suficiente: quedan 0.' },
      { status: 400, statusText: 'Bad Request' },
    );

    expect(store.cartItems()).toEqual([]);
    expect(errorMessage(toaster.error.mock.calls[0][0])).toBe('No hay stock suficiente: quedan 0.');
  });

  /** HU-13 · Escenario: Subir la cantidad dentro del stock. */
  it('raises a quantity within the stock and updates the total', () => {
    setup(true, [aCartItem({ productId: 15, productPrice: 1000, quantity: 1, stock: 5 })]);

    store.updateCartQty(15, 3);
    backend.expectOne({ method: 'PUT', url: `${api}/cart/15?qty=3` }).flush(null);

    expect(store.cartItems()[0].quantity).toBe(3);
    expect(store.cartTotal()).toBe(3000);
  });

  /** HU-13 · Escenario: Pedir más que el stock. */
  it('keeps the previous quantity on a 409, says how many are left and reads the cart again', () => {
    setup(true, [aCartItem({ productId: 15, quantity: 2, stock: 6 })]);

    store.updateCartQty(15, 6);
    backend.expectOne({ method: 'PUT', url: `${api}/cart/15?qty=6` }).flush(
      { status: 409, title: 'Conflicto', detail: 'Solo quedan 5.' },
      { status: 409, statusText: 'Conflict' },
    );

    expect(store.cartItems()[0].quantity).toBe(2);
    expect(errorMessage(toaster.error.mock.calls[0][0])).toBe('Solo quedan 5.');
    backend.expectOne({ method: 'GET', url: `${api}/cart` }).flush([aCartItem({ productId: 15, quantity: 2, stock: 5 })]);
    expect(store.cartItems()[0].stock).toBe(5);
  });

  /** HU-13 · Escenario: Un producto se desactiva mientras está en el carrito. */
  it('leaves unavailable items out of units and total, and blocks the checkout', () => {
    setup(true, [
      aCartItem({ productId: 15, productPrice: 1000, quantity: 2 }),
      aCartItem({ productId: 16, productName: 'Gorra roja', productPrice: 5000, quantity: 1, available: false }),
    ]);

    expect(store.cartItems()).toHaveLength(2);
    expect(store.cartCount()).toBe(2);
    expect(store.cartTotal()).toBe(2000);
    expect(store.cartBlocked()).toBe(true);

    store.removeFromCart(16);
    backend.expectOne({ method: 'DELETE', url: `${api}/cart/16` }).flush(null);

    expect(store.cartBlocked()).toBe(false);
  });

  it('blocks the checkout while a quantity is above the stock left', () => {
    setup(true, [aCartItem({ quantity: 4, stock: 2 })]);

    expect(store.cartBlocked()).toBe(true);
  });

  it('keeps the reason when the cart cannot be read', () => {
    setup(false);
    loggedIn.set(true);
    TestBed.tick();

    backend.expectOne(`${api}/wishlist`).flush([]);
    expect(store.cartLoading()).toBe(true);
    backend.expectOne(`${api}/cart`).flush(
      { status: 503, detail: 'No pudimos leer tu carrito.' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    backend.match((r) => r.url.includes('/recommendations')).forEach((r) => r.flush({ recommended: [], othersBought: [] }));

    expect(store.cartLoading()).toBe(false);
    expect(store.cartError()).toBe('Algo falló de nuestro lado.');
  });

  it('places the order: address first, then the order; empties the cart and goes to the receipt', () => {
    setup(true);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    store.placeOrder({ firstName: 'Lucía', lastName: 'Pérez', address: 'San Martín 123', cityId: 1, zipCode: '2000' });
    backend.expectOne({ method: 'POST', url: `${api}/addresses` }).flush({ id: 9 });
    const order = { id: 1, total: 97800, items: [] };
    const req = backend.expectOne({ method: 'POST', url: `${api}/orders` });
    expect(req.request.body).toEqual({ addressId: 9 });
    req.flush(order);

    expect(store.cartItems()).toEqual([]);
    expect(navigate).toHaveBeenCalledWith(['/order-success'], { state: { order } });
  });

  it('keeps the cart, shows why and marks the item when the order fails with 409 (HU-13)', () => {
    setup(true);

    store.placeOrder({ firstName: 'Lucía', lastName: 'Pérez', address: 'San Martín 123', cityId: 1, zipCode: '2000' });
    backend.expectOne(`${api}/addresses`).flush({ id: 9 });
    backend.expectOne(`${api}/orders`).flush(
      { status: 409, detail: 'Sin stock de Remera lino.' },
      { status: 409, statusText: 'Conflict' },
    );

    expect(store.cartItems()).toHaveLength(1);
    expect(store.error()).toBe('Sin stock de Remera lino.');
    expect(store.loading()).toBe(false);

    backend.expectOne({ method: 'GET', url: `${api}/cart` }).flush([aCartItem({ quantity: 2, stock: 1 })]);
    expect(store.cartBlocked()).toBe(true);
  });

  it('clears cart and wishlist when the session ends', () => {
    setup(true);
    expect(store.cartItems()).toHaveLength(1);

    loggedIn.set(false);
    TestBed.tick();

    expect(store.cartItems()).toEqual([]);
    expect(store.wishListItems()).toEqual([]);
  });
});
