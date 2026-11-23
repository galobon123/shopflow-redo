import { EnvironmentProviders, Provider } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideHotToastConfig } from '@ngxpert/hot-toast';
import { ProductResponse } from '../core/models/product.model';
import { CartItem } from '../core/models/cart.model';

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

/** A session token like the backend's (the front only decodes it, never checks the signature). */
export function aToken(claims: Record<string, unknown> = {}): string {
  const encode = (value: object) =>
    btoa(String.fromCharCode(...new TextEncoder().encode(JSON.stringify(value))))
      .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const payload = { sub: 'ana@mail.com', role: 'ROLE_USER', name: 'Ana', userId: 1, emailVerified: true, exp: 4102444800, ...claims };
  return `${encode({ alg: 'HS256' })}.${encode(payload)}.x`;
}
