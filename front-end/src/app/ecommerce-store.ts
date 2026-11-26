import { errorMessage, problemOf } from './core/util/error-message';
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
import { AddressRequest, AddressResponse } from './core/models/address.model';
import { OrderResponse } from './core/models/order.model';
import { forkJoin, Observable, of } from 'rxjs';
import { map, catchError, switchMap, finalize, tap } from 'rxjs/operators';
import { Router } from '@angular/router';

type EcommerceState = {
  categories: CategoryResponse[];
  wishListItems: WishListResponse[];
  wishListLoading: boolean;
  wishListError: string | null;
  /**
   * Products whose favourite is being saved or removed right now. HU-14 · The heart already shows the new state
   * (optimistic) but ignores taps until the server answers, so an add and a remove can't cross.
   */
  wishListSaving: number[];
  /** HU-14 · "Vaciar favoritos" is waiting for the server; the list stays until it answers. */
  wishListClearing: boolean;
  cartItems: CartItem[];
  cartLoading: boolean;
  cartError: string | null;
  /** HU-12 · The guest cart is joining the account's; a plain read meanwhile could arrive late and stale. */
  cartMerging: boolean;
  /** HU-12 · Numbers the cart reads and merges: only the latest answer is kept (a read sent before the merge loses). */
  cartRequest: number;
  /** HU-13 · What changed in the cart because the stock moved; shown on top of the cart page until dismissed. */
  cartNotices: string[];
  recommendations: RecommendationsResponse;
  /** HU-16 · "Mis direcciones", the last used first (the checkout preselects it). */
  addresses: AddressResponse[];
  addressesLoading: boolean;
  addressesError: string | null;
  /** HU-17 · Names the purchase being confirmed (Idempotency-Key): made on entering the checkout, kept on retries. */
  checkoutKey: string;
  /** HU-17 · The cart the key was first sent with: another cart is another purchase, with a new key. */
  checkoutCart: string | null;
  loading: boolean;
  error: string | null;
};

const initialState: EcommerceState = {
  categories: [],
  wishListItems: [],
  wishListLoading: false,
  wishListError: null,
  wishListSaving: [],
  wishListClearing: false,
  cartItems: [],
  cartLoading: false,
  cartError: null,
  cartMerging: false,
  cartRequest: 0,
  cartNotices: [],
  recommendations: { sections: [] },
  addresses: [],
  addressesLoading: false,
  addressesError: null,
  checkoutKey: '',
  checkoutCart: null,
  loading: false,
  error: null,
};

/**
 * HU-13 · The cart follows the stock as soon as it's read: a quantity above what's left drops to it, and a
 * sold-out product leaves the cart, each with a notice to show. The server is told too (fire and forget: if it fails,
 * the next read fits it again). A deactivated product that still has stock stays, as "No disponible".
 */
function fitToStock(items: CartItem[], cartService: CartService) {
  const notices: string[] = [];
  const fitted = items.flatMap((item) => {
    if (!item.available && item.stock === 0) {
      cartService.remove(item.productId).subscribe({ error: () => {} });
      notices.push(`Se agotó ${item.productName}: lo sacamos de tu carrito.`);
      return [];
    }
    if (item.available && item.quantity > item.stock) {
      cartService.updateQty(item.productId, item.stock).subscribe({ error: () => {} });
      notices.push(item.stock === 1
        ? `De ${item.productName} queda solo 1: ajustamos tu carrito.`
        : `De ${item.productName} quedan solo ${item.stock}: ajustamos tu carrito.`);
      return [{ ...item, quantity: item.stock }];
    }
    return [item];
  });
  return { items: fitted, notices };
}

/** HU-17 · What a purchase is buying, to tell when the cart is no longer the one a key was used with. */
const cartSignature = (items: CartItem[]) =>
  items.map(item => `${item.productId}x${item.quantity}`).sort().join(',');

/** HU-13 · Unavailable items stay listed but don't count towards units or total. */
const purchasable = (items: CartItem[]) => items.filter(item => item.available);

export const EcommerceStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),

  withComputed((store) => ({
  wishListCount: computed(() => store.wishListItems().length),
  cartCount: computed(() => purchasable(store.cartItems()).reduce((sum, item) => sum + item.quantity, 0)),
  cartTotal: computed(() => purchasable(store.cartItems()).reduce((sum, item) =>
    sum + item.productPrice * item.quantity, 0)),
  /** HU-13 · Checkout waits until unavailable items are removed and no quantity is above the stock left. */
  cartBlocked: computed(() => store.cartItems().some(item => !item.available || item.quantity > item.stock)),
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
    toaster = inject(Toaster)) => {
  /**
   * A cart just read from the server, fitted to the stock. If something changed or can't be bought while paying,
   * back to the cart, where the notices make sense: never pay for something that's no longer there.
   */
  const setCart = (items: CartItem[]): boolean => {
    const { items: fitted, notices } = fitToStock(items, cartService);
    const changed = notices.length > 0;
    patchState(store, { cartItems: fitted });
    if (changed) patchState(store, { cartNotices: notices });
    if ((changed || store.cartBlocked()) && router.url.startsWith('/checkout')) router.navigate(['/cart']);
    return changed;
  };
  /** HU-17 · A new purchase attempt: a fresh Idempotency-Key, not yet tied to a cart. */
  const newCheckoutKey = () => patchState(store, { checkoutKey: crypto.randomUUID(), checkoutCart: null });

  return {
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
    patchState(store, { wishListLoading: true, wishListError: null });
    wishListService.getWishlist().pipe(
      finalize(() => patchState(store, { wishListLoading: false })),
    ).subscribe({
      next: (items) => patchState(store, { wishListItems: items }),
      // Only the favourites page shows it; everywhere else the hearts just start empty.
      error: (err) => patchState(store, { wishListError: errorMessage(err) }),
    });
  },
  /** HU-14 · Optimistic: the heart is marked right away; if the server says no, it goes back and says why. */
  addToWishList : (product: ProductResponse) => { 
    if (!authStore.isAuthenticated()) {
    toaster.info('Iniciá sesión para guardar favoritos.');
    return;
  }
  if (store.wishListSaving().includes(product.id)) return;
  if (store.wishListItems().some(item => item.productId === product.id)) return;
  const item: WishListResponse = {
    id: 0,
    productId: product.id,
    productName: product.name,
    productPrice: product.price,
    productImageUrl: product.imageUrl ?? '',
    productStock: product.stock,
    createdAt: new Date().toISOString(),
  };
  patchState(store, {
    wishListItems: [...store.wishListItems(), item],
    wishListSaving: [...store.wishListSaving(), product.id],
  });
  wishListService.add(product.id).pipe(
    finalize(() => patchState(store, { wishListSaving: store.wishListSaving().filter(id => id !== product.id) })),
  ).subscribe({
      next: () => toaster.success('Agregado a favoritos.'),
      error: (err) => {
        patchState(store, { wishListItems: store.wishListItems().filter(p => p.productId !== product.id) });
        toaster.error(err);
      },
    });
  },
  /** HU-14 · Optimistic too: it leaves the list right away and comes back to its place if the server fails. */
  removeFromWishList : (productId: number) => {
      if (store.wishListSaving().includes(productId)) return;
      const index = store.wishListItems().findIndex(p => p.productId === productId);
      if (index === -1) return;
      const removed = store.wishListItems()[index];
      patchState(store, {
        wishListItems: store.wishListItems().filter(p => p.productId !== productId),
        wishListSaving: [...store.wishListSaving(), productId],
      });
      wishListService.remove(productId).pipe(
        finalize(() => patchState(store, { wishListSaving: store.wishListSaving().filter(id => id !== productId) })),
      ).subscribe({
      next: () => toaster.success('Quitado de favoritos.'),
      error: (err) => {
        const items = [...store.wishListItems()];
        items.splice(Math.min(index, items.length), 0, removed);
        patchState(store, { wishListItems: items });
        toaster.error(err);
      },
    });
  },
  /**
   * HU-14 · One request, all or nothing. Not optimistic: emptying everything and bringing it back on an error
   * would flash the empty page, so the list stays until the server answers.
   */
  clearWishlist: () => {
    if (store.wishListClearing()) return;
    patchState(store, { wishListClearing: true });
    wishListService.clear().pipe(
      finalize(() => patchState(store, { wishListClearing: false })),
    ).subscribe({
      next: () => {
        patchState(store, { wishListItems: [] });
        toaster.success('Vaciaste tus favoritos.');
      },
      error: () => toaster.error('No pudimos vaciar tus favoritos, probá de nuevo.'),
    });
  },
  /** HU-12 · Signed in it's the account's cart; otherwise this browser's guest cart. */
  loadCart(): void {
  if (store.cartMerging()) return;
  const request = store.cartRequest() + 1;
  patchState(store, { cartLoading: true, cartError: null, cartRequest: request });
  cartService.getCart().pipe(
    finalize(() => patchState(store, { cartLoading: false })),
  ).subscribe({
    next: (items) => { if (request === store.cartRequest()) setCart(items); },
    error: (err) => { if (request === store.cartRequest()) patchState(store, { cartError: errorMessage(err) }); },
  });
  },
  /** `quantity` comes from the product page's selector (HU-11); the cards add one. */
  addToCart(product: ProductResponse, quantity = 1): void {
    cartService.add(product.id, quantity).subscribe({
      next: () => {
        const existing = store.cartItems().find(item => item.productId === product.id);
        if (existing) {
          patchState(store, {
            cartItems: store.cartItems().map(item =>
              item.productId === product.id
                ? { ...item, quantity: item.quantity + quantity }
                : item
            )
          });
        } else {
          const newItem: CartItem = {
            productId: product.id,
            productName: product.name,
            productPrice: product.price,
            productImageUrl: product.imageUrl ?? '',
            quantity,
            stock: product.stock,
            available: true,
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
      // HU-13 · The quantity stays as it was; a 409 means the stock moved, so the cart is read again.
      error: (err) => {
        toaster.error(err);
        if (err?.status === 409) {
          cartService.getCart().subscribe({ next: (items) => setCart(items) });
        }
      },
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
              stock: item.productStock,
              available: true,
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
  /** HU-16 · Read when the checkout opens: they change only there, and a session may never need them. */
  loadAddresses(): void {
    patchState(store, { addressesLoading: true, addressesError: null });
    addressService.getAddresses().pipe(
      finalize(() => patchState(store, { addressesLoading: false })),
    ).subscribe({
      next: (addresses) => patchState(store, { addresses }),
      error: (err) => patchState(store, { addressesError: errorMessage(err) }),
    });
  },
  /** HU-17 · Entering the checkout starts a new purchase attempt. */
  startCheckout(): void {
    newCheckoutKey();
  },
  /**
   * HU-16 · Ships to a saved address (its id) or to a new one, saved first. The new one joins "Mis direcciones"
   * at the top right away, so if the order fails, trying again reuses it instead of saving it twice.
   * HU-17 · Every retry of the same purchase sends the same Idempotency-Key, so the server never buys twice;
   * a new key comes after an order is made or when the cart is not the one the key was used with.
   */
  placeOrder(shipTo: number | AddressRequest): void {
    const cart = cartSignature(store.cartItems());
    if (!store.checkoutKey() || (store.checkoutCart() !== null && store.checkoutCart() !== cart)) newCheckoutKey();
    const key = store.checkoutKey();
    patchState(store, { loading: true, error: null, checkoutCart: cart });
    const addressId: Observable<number> = typeof shipTo === 'number' ? of(shipTo) : addressService.createAddress(shipTo).pipe(
      tap((created) => patchState(store, { addresses: [created, ...store.addresses()] })),
      map((created) => created.id),
    );
    addressId.pipe(
      switchMap((id) => orderService.createOrder(id, key)),
    ).subscribe({
      next: (order) => {
        patchState(store, { cartItems: [], loading: false });
        newCheckoutKey();
        router.navigate(['/order-success'], { state: { order } });
        toaster.success('¡Listo! Recibimos tu pedido.');
      },
      error: (err) => {
        patchState(store, { loading: false, error: errorMessage(err) });
        // HU-17 · The same purchase is still being made (another click or tab): stay, and the next try with this
        // key answers that order.
        if (problemOf(err)?.reason === 'order-in-progress') {
          toaster.info(`${errorMessage(err)} Esperá unos segundos y confirmá de nuevo: no vas a comprar dos veces.`);
          return;
        }
        toaster.error(err);
        // HU-13 · A 409 means a product went unavailable or short: back to the cart, read again so it shows which one.
        if (err?.status === 409) {
          router.navigate(['/cart']);
          cartService.getCart().subscribe({ next: (items) => setCart(items) });
        }
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
  /**
   * HU-12 · Runs every time a session starts, whatever the way in (password, Google, new-device code,
   * email link): the guest cart joins the account's, and the browser starts a new, empty guest cart.
   */
  mergeCart(): void {
    patchState(store, { cartMerging: true, cartLoading: true, cartError: null, cartRequest: store.cartRequest() + 1 });
    cartService.merge().pipe(
      finalize(() => patchState(store, { cartMerging: false, cartLoading: false })),
    ).subscribe({
      next: ({ items, notices }) => {
        cartService.newGuestCart();
        setCart(items);
        notices.forEach((notice) => toaster.info(notice));
      },
      error: (err) => patchState(store, { cartError: errorMessage(err) }),
    });
  },
  /**
   * Checkout needs a session: a guest goes through the login (authGuard) and comes back with the cart joined.
   * HU-13 · The stock is read again first: if it moved since the cart page loaded, the cart is fitted and stays.
   */
  dismissCartNotices(): void {
    patchState(store, { cartNotices: [] });
  },
  proceedToCheckout(): void {
    cartService.getCart().subscribe({
      next: (items) => {
        if (!setCart(items) && !store.cartBlocked()) router.navigate(['/checkout']);
      },
      error: (err) => toaster.error(err),
    });
  }
  };
}),
  withHooks({
    onInit(store) {
      const authStore = inject(AuthStore);

      store.loadCategories();

      effect(() => {
        if (authStore.isAuthenticated()) {
          untracked(() => {
            store.loadWishlist();
            store.mergeCart();
            store.loadRecommendations();
          });
        } else {
          untracked(() => {
            patchState(store, {
              cartItems: [],
              cartError: null,
              cartNotices: [],
              wishListItems: [],
              wishListError: null,
              recommendations: { sections: [] },
              addresses: [],
              addressesError: null,
            });
            store.loadCart();
          });
        }
      });
    },
  }),
)

