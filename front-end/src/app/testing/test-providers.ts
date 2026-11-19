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
    ...overrides,
  };
}
