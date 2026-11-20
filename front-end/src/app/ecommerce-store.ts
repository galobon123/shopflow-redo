import { errorMessage } from './core/util/error-message';
import { signalStore, withState, withMethods, withComputed, patchState, withHooks } from '@ngrx/signals';
import { computed, inject, untracked, effect } from '@angular/core';
import { ProductResponse } from './core/models/product.model';
import { CategoryResponse } from './core/models/category.model';
import { CategoryService } from './core/services/category.service';
import { WishListService } from './core/services/wishlist.service';
import { AuthStore } from './auth-store';
import { WishListResponse } from './core/models/wishlist.model';
import { Toaster } from './core/services/toaster.service';
import { RecommendationService } from './core/services/recommendation.service';
import { RecommendationsResponse } from './core/models/recommendation.model';
import { CartItem } from './core/models/cart.model';
import { CartService } from './core/services/cart.service';
import { OrderService } from './core/services/order.service';
import { AddressService } from './core/services/address.service';
import { AddressRequest } from './core/models/address.model';
import { OrderResponse } from './core/models/order.model';
import { forkJoin, of } from 'rxjs';
import { map, catchError, switchMap, finalize } from 'rxjs/operators';
import { Router } from '@angular/router';

type EcommerceState = {
  categories: CategoryResponse[];
  wishListItems: WishListResponse[];
  /** Products whose favourite is being saved or removed right now (the heart waits for the server). */
  wishListSaving: number[];
  cartItems: CartItem[];
  recommendations: RecommendationsResponse;
  loading: boolean;
  error: string | null;
};

const initialState: EcommerceState = {
  categories: [],
  wishListItems: [],
  wishListSaving: [],
  cartItems: [],
  recommendations: { recommended: [], othersBought: [] },
  loading: false,
  error: null,
};

export const EcommerceStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),

  withComputed((store) => ({
  wishListCount: computed(() => store.wishListItems().length),
  cartCount: computed(() => store.cartItems().reduce((sum, item) => sum + item.quantity, 0)),
  cartTotal: computed(() => store.cartItems().reduce((sum, item) => 
    sum + item.productPrice * item.quantity, 0)),
})),

  withMethods((store,
    categoryService = inject(CategoryService), 
    wishListService = inject(WishListService),
    authStore = inject(AuthStore),
    recommendationService = inject(RecommendationService),
    cartService = inject(CartService),
    orderService = inject(OrderService),
    addressService = inject(AddressService),
    router = inject(Router),
    toaster = inject(Toaster)) => ({
  /** The catalog itself is paged by the server (HU-10, products-grid); only categories live here. */
  loadCategories(): void {
    categoryService.getAll().subscribe({
      next: (categories) => patchState(store, { categories }),
      error: () => { /* the catalog still works without the category list */ },
    });
  },
  loadRecommendations(): void {
  if (!authStore.isAuthenticated()) return;
  recommendationService.getRecommendations().subscribe({
    next: (recs) => patchState(store, { recommendations: recs }),
    error: () => { /* silencioso */ },
  });
  },
  loadWishlist(): void {
  if (!authStore.isAuthenticated()) return;
    wishListService.getWishlist().subscribe({
      next: (items) => patchState(store, { wishListItems: items }),
      error: () => { /* silencioso, no bloquea la app */ },
    });
  },
  addToWishList : (product: ProductResponse) => { 
    if (!authStore.isAuthenticated()) {
    toaster.info('Iniciá sesión para guardar favoritos.');
    return;
  }
  if (store.wishListSaving().includes(product.id)) return;
  patchState(store, { wishListSaving: [...store.wishListSaving(), product.id] });
  wishListService.add(product.id).pipe(
    finalize(() => patchState(store, { wishListSaving: store.wishListSaving().filter(id => id !== product.id) })),
  ).subscribe({
      next: () => {
        const item: WishListResponse = {
          id: 0,
          productId: product.id,
          productName: product.name,
          productPrice: product.price,
          productImageUrl: product.imageUrl ?? '',
          productStock: product.stock,
          createdAt: new Date().toISOString(),
        };
        patchState(store, { wishListItems: [...store.wishListItems(), item] });
        toaster.success('Agregado a favoritos.');
      },
      error: (err) => toaster.error(err),
    });
  },
  removeFromWishList : (productId: number) => {
      if (store.wishListSaving().includes(productId)) return;
      patchState(store, { wishListSaving: [...store.wishListSaving(), productId] });
      wishListService.remove(productId).pipe(
        finalize(() => patchState(store, { wishListSaving: store.wishListSaving().filter(id => id !== productId) })),
      ).subscribe({
      next: () => {
        patchState(store, { 
          wishListItems: store.wishListItems().filter(p => p.productId !== productId) 
        });
        toaster.success('Quitado de favoritos.');
      },
      error: (err) => toaster.error(err),
    });
  },
  clearWishlist: () => {
      store.wishListItems().forEach(item => {
      wishListService.remove(item.productId).subscribe();
    });
    patchState(store, { wishListItems: [] });
  },
  loadCart(): void {
  if (!authStore.isAuthenticated()) return;
  cartService.getCart().subscribe({
    next: (items) => patchState(store, { cartItems: items }),
    error: () => { /* silencioso */ },
  });
  },
  addToCart(product: ProductResponse): void {
    if (!authStore.isAuthenticated()) {
      toaster.info('Iniciá sesión para agregar productos al carrito.');
      return;
    }
    cartService.add(product.id).subscribe({
      next: () => {
        const existing = store.cartItems().find(item => item.productId === product.id);
        if (existing) {
          patchState(store, {
            cartItems: store.cartItems().map(item =>
              item.productId === product.id
                ? { ...item, quantity: item.quantity + 1 }
                : item
            )
          });
        } else {
          const newItem: CartItem = {
            productId: product.id,
            productName: product.name,
            productPrice: product.price,
            productImageUrl: product.imageUrl ?? '',
            quantity: 1,
          };
          patchState(store, { cartItems: [...store.cartItems(), newItem] });
        }
        toaster.success('Agregado al carrito.');
      },
      error: (err) => toaster.error(err),
    });
  },
  removeFromCart(productId: number): void {
    cartService.remove(productId).subscribe({
      next: () => {
        patchState(store, { cartItems: store.cartItems().filter(item => item.productId !== productId) });
        toaster.success('Quitado del carrito.');
      },
      error: (err) => toaster.error(err),
    });
  },
  updateCartQty(productId: number, qty: number): void {
    cartService.updateQty(productId, qty).subscribe({
      next: () => {
        if (qty <= 0) {
          patchState(store, { cartItems: store.cartItems().filter(item => item.productId !== productId) });
        } else {
          patchState(store, {
            cartItems: store.cartItems().map(item =>
              item.productId === productId ? { ...item, quantity: qty } : item
            )
          });
        }
      },
      error: (err) => toaster.error(err),
    });
  },
  clearCart(): void {
    cartService.clear().subscribe({
      next: () => {
        patchState(store, { cartItems: [] });
        toaster.success('Vaciaste el carrito.');
      },
      error: (err) => toaster.error(err),
    });
  },
  addAllWishlistToCart(wishlistItems: WishListResponse[]): void {
    if (!authStore.isAuthenticated()) {
      toaster.info('Iniciá sesión para usar el carrito.');
      return;
    }
    if (wishlistItems.length === 0) {
      toaster.info('Tu lista de favoritos está vacía.');
      return;
    }
    const requests = wishlistItems.map((item) =>
      cartService.add(item.productId).pipe(
        map(() => ({ status: 'success' as const, item })),
        catchError(() => of({ status: 'error' as const, item }))
      )
    );

    forkJoin(requests).subscribe((results) => {
      const successfulItems = results
        .filter((r) => r.status === 'success')
        .map((r) => r.item);

      const errorsCount = results.length - successfulItems.length;

      if (successfulItems.length === 0) {
        toaster.error('No pudimos agregar los productos al carrito.');
        return;
      }

      patchState(store, (state) => {
        const currentItems = [...state.cartItems];

        successfulItems.forEach((item) => {
          const index = currentItems.findIndex((c) => c.productId === item.productId);
          if (index !== -1) {
            currentItems[index] = {
              ...currentItems[index],
              quantity: currentItems[index].quantity + 1,
            };
          } else {
            currentItems.push({
              productId: item.productId,
              productName: item.productName,
              productPrice: item.productPrice,
              productImageUrl: item.productImageUrl,
              quantity: 1,
            });
          }
        });

        return { cartItems: currentItems };
      });

      successfulItems.forEach((item) => {
        wishListService.remove(item.productId).subscribe();
      });

      patchState(store, {
        wishListItems: store.wishListItems().filter(
          (item) => !successfulItems.some((s) => s.productId === item.productId)
        ),
      });


      if (errorsCount === 0) {
        toaster.success(`Agregaste ${successfulItems.length} productos al carrito.`);
      } else {
        toaster.info(
          `Agregaste ${successfulItems.length} productos al carrito; ${errorsCount} no se pudieron agregar.`
        );
      }
    });
  },
  placeOrder(address: AddressRequest): void {
    patchState(store, { loading: true, error: null });
    addressService.createAddress(address).pipe(
      switchMap((createdAddress) =>
        orderService.createOrder(createdAddress.id).pipe(
          map((order) => ({ order, error: null as string | null }))
        )
      )
    ).subscribe({
      next: ({ order }) => {
        patchState(store, { cartItems: [], loading: false });
        router.navigate(['/order-success'], { state: { order } });
        toaster.success('¡Listo! Recibimos tu pedido.');
      },
      error: (err) => {
        patchState(store, { loading: false, error: errorMessage(err) });
        toaster.error(err);
      },
    });
  },
  moveToWishlist(item: CartItem): void {
    if (!authStore.isAuthenticated()) {
      toaster.info('Iniciá sesión para usar esta función.');
      return;
    }
    wishListService.add(item.productId).pipe(
      switchMap(() => cartService.remove(item.productId))
    ).subscribe({
      next: () => {
        patchState(store, (state) => {
          const existsInWishlist = state.wishListItems.some(
            (w) => w.productId === item.productId
          );

          const newWishItem: WishListResponse = {
            id: 0,
            productId: item.productId,
            productName: item.productName,
            productPrice: item.productPrice,
            productImageUrl: item.productImageUrl,
            productStock: 0,
            createdAt: new Date().toISOString(),
          };

          return {
            wishListItems: existsInWishlist 
              ? state.wishListItems 
              : [...state.wishListItems, newWishItem],
            cartItems: state.cartItems.filter((c) => c.productId !== item.productId),
          };
        });

        toaster.success('Movido a favoritos.');
      },
      error: (err) => {
        toaster.error(err);
      },
    });
  },
  proceedToCheckout(): void {
    router.navigate(['/checkout']);
  }
})),
  withHooks({
    onInit(store) {
      const authStore = inject(AuthStore);

      store.loadCategories();

      effect(() => {
        if (authStore.isAuthenticated()) {
          untracked(() => {
            store.loadWishlist();
            store.loadCart();
            store.loadRecommendations();
          });
        } else {
          untracked(() => {
            patchState(store, {
              cartItems: [],
              wishListItems: [],
              recommendations: { recommended: [], othersBought: [] },
            });
          });
        }
      });
    },
  }),
)

