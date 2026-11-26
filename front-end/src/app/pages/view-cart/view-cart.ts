import { ChangeDetectionStrategy, Component, ElementRef, afterRenderEffect, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ShoppingCart } from 'lucide-angular';
import { BackButton } from "../../components/back-button/back-button";
import { ListCartItems } from "./list-cart-items/list-cart-items";
import { TeaseWishlist } from './tease-wishlist/tease-wishlist';
import { SummarizeOrder } from '../../components/summarize-order/summarize-order';
import { SfAlert, SfAlertAction, SfButton, SfEmptyState, SfErrorState, SfSkeleton } from '../../ui';
import { EcommerceStore } from '../../ecommerce-store';
import { AuthStore } from '../../auth-store';

@Component({
  selector: 'app-view-cart',
  imports: [BackButton, ListCartItems, TeaseWishlist, SummarizeOrder, RouterLink, SfAlert, SfAlertAction, SfButton, SfEmptyState, SfErrorState, SfSkeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './view-cart.html',
  styleUrl: './view-cart.css',
})
export class ViewCart {
  store = inject(EcommerceStore);
  protected readonly authStore = inject(AuthStore);

  /** HU-13 · The stock or the product may have changed since the cart was last read: ask again on every visit. */
  constructor() {
    this.store.loadCart();

    // HU-13 · Taking out the last product removes the whole list; the focus goes to the empty state's way out.
    const host: HTMLElement = inject(ElementRef).nativeElement;
    let hadItems = false;
    afterRenderEffect(() => {
      const hasItems = this.store.cartItems().length > 0;
      if (hadItems && !hasItems && (!document.activeElement || document.activeElement === document.body)) {
        host.querySelector<HTMLElement>('sf-empty-state a')?.focus();
      }
      hadItems = hasItems;
    });
  }

  protected readonly emptyIcon = ShoppingCart;
}
