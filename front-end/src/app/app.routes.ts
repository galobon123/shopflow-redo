import { provideRouter, Routes, withComponentInputBinding } from '@angular/router';
import { authGuard, adminGuard, guestGuard } from './core/guards/auth.guard';


export const routes: Routes = [
  {
    path: '',
    redirectTo: 'products/all',
    pathMatch: 'full'
  },
  {
    path: 'ui-kit',
    title: 'UI Kit · ShopFlow',
    loadComponent: () => import('./pages/ui-kit/ui-kit').then(m => m.UiKit)
  },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/login/login')
      .then(m => m.Login)
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/register/register')
      .then(m => m.Register)
  },
  {
    path: 'products/:categoryName',
    loadComponent: () => import('./pages/products-grid/products-grid')
    .then(m => m.ProductsGrid)
  },
  {
    path: 'checkout',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/checkout/checkout')
    .then(m => m.Checkout)
  },
  {
    path: 'order-success',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/order-success/order-success')
    .then(m => m.OrderSuccess)
  },
  {
    path: 'wishlist',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/my-wishlist/my-wishlist')
    .then(m => m.MyWishlist)
  },
  {
    path: 'cart',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/view-cart/view-cart')
    .then(m => m.ViewCart)
  },
  // {
    //   path: 'admin/products',
    //   loadComponent: () => import('./features/products/product-form/product-form.component')
    //     .then(m => m.ProductFormComponent),
    //   canActivate: [authGuard, adminGuard]
    // },
    {
      path: '**',
      redirectTo: 'products/all'
    }
];