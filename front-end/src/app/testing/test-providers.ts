import { EnvironmentProviders, Provider } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideHotToastConfig } from '@ngxpert/hot-toast';
import { ProductResponse } from '../core/models/product.model';
import { CartItem } from '../core/models/cart.model';
import { WishListResponse } from '../core/models/wishlist.model';
import { OrderItem, OrderResponse } from '../core/models/order.model';

/** What every page/component test needs: router, a fake backend (HttpTestingController) and toasts. */
export function testProviders(): (Provider | EnvironmentProviders)[] {
  return [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), provideHotToastConfig()];
}

export function aProduct(overrides: Partial<ProductResponse> = {}): ProductResponse {
  return {
    id: 15,
    name: 'Remera lino',
    description: 'Remera de lino natural',
    price: 48900,
    stock: 3,
    categoryName: 'Remeras',
    active: true,
    createdAt: '2026-11-01T10:00:00',
    ...overrides,
  };
}

export function aCartItem(overrides: Partial<CartItem> = {}): CartItem {
  return {
    productId: 15,
    productName: 'Remera lino',
    productPrice: 48900,
    productImageUrl: '',
    quantity: 2,
    stock: 10,
    available: true,
    ...overrides,
  };
}

export function aWishItem(overrides: Partial<WishListResponse> = {}): WishListResponse {
  return {
    id: 1,
    productId: 15,
    productName: 'Remera lino',
    productPrice: 48900,
    productImageUrl: '',
    productStock: 3,
    createdAt: '2026-11-01T10:00:00',
    ...overrides,
  };
}

/**
 * HU-19 · Two of one product and one of another, waiting for its payment. Dates are instants (UTC); the payment
 * window ends far ahead, so the order is still payable whatever day the tests run.
 */
export function anOrder(overrides: Partial<OrderResponse> = {}): OrderResponse {
  return {
    id: 7,
    createdAt: '2026-11-01T02:30:00Z',
    total: 146700,
    status: 'PENDIENTE_PAGO',
    expiresAt: '2099-11-01T03:00:00Z',
    address: {
      id: 3, firstName: 'Ana', lastName: 'Gómez', address: 'San Martín 123', stateId: 21, cityId: 300,
      cityName: 'Rosario', stateName: 'Santa Fe', zipCode: 'S2000ABC',
    },
    items: [
      anOrderItem(),
      anOrderItem({ productId: 16, productName: 'Pantalón cargo', imageUrl: null, quantity: 1, unitPrice: 48900, subtotal: 48900 }),
    ],
    ...overrides,
  };
}

export function anOrderItem(overrides: Partial<OrderItem> = {}): OrderItem {
  return {
    productId: 15,
    productName: 'Remera lino',
    imageUrl: 'https://img.example.com/remera.jpg',
    quantity: 2,
    unitPrice: 48900,
    subtotal: 97800,
    canReview: false,
    ...overrides,
  };
}

/** A session token like the backend's (the front only decodes it, never checks the signature). */
export function aToken(claims: Record<string, unknown> = {}): string {
  const encode = (value: object) =>
    btoa(String.fromCharCode(...new TextEncoder().encode(JSON.stringify(value))))
      .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const payload = { sub: 'ana@mail.com', role: 'ROLE_USER', name: 'Ana', userId: 1, emailVerified: true, exp: 4102444800, ...claims };
  return `${encode({ alg: 'HS256' })}.${encode(payload)}.x`;
}
