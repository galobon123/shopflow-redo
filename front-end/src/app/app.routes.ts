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
    path: 'forgot-password',
    title: 'Recuperar contraseña · ShopFlow',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/forgot-password/forgot-password')
      .then(m => m.ForgotPassword)
  },
  {
    // HU-09 · The link in the email. Not guest-only, like /verify-email: it may be opened while signed in.
    path: 'reset-password',
    title: 'Nueva contraseña · ShopFlow',
    loadComponent: () => import('./pages/reset-password/reset-password')
      .then(m => m.ResetPassword)
  },
  {
    // HU-31 · The link in the email. Not guest-only: it may be opened while signed in.
    path: 'verify-email',
    title: 'Confirmar email · ShopFlow',
    loadComponent: () => import('./pages/verify-email/verify-email')
      .then(m => m.VerifyEmail)
  },
  {
    path: 'products/:categoryName',
    loadComponent: () => import('./pages/products-grid/products-grid')
    .then(m => m.ProductsGrid)
  },
  {
    // HU-11 · Product page. The title becomes the product's name once it loads.
    path: 'product/:id',
    title: 'Producto · ShopFlow',
    loadComponent: () => import('./pages/product-detail/product-detail')
    .then(m => m.ProductDetail)
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
    // HU-12 · Open to guests: their cart lives under X-Cart-Id. Checkout still asks to sign in.
    path: 'cart',
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