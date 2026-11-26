import { ChangeDetectionStrategy, Component, ElementRef, afterRenderEffect, inject, viewChild } from '@angular/core';
import { ViewPanel } from '../../../directives/view-panel';
import { ShowCartItem } from '../../show-cart-item/show-cart-item';
import { EcommerceStore } from '../../../ecommerce-store';

@Component({
  selector: 'app-list-cart-items',
  imports: [ViewPanel, ShowCartItem],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './list-cart-items.html',
  styleUrl: './list-cart-items.css',
})
export class ListCartItems {
  store = inject(EcommerceStore);

  private readonly host: HTMLElement = inject(ElementRef).nativeElement;
  private readonly heading = viewChild.required<ElementRef<HTMLElement>>('heading');
  /** The row whose remove/move button was pressed, until it's gone from the cart. */
  private leaving: { productId: number; index: number } | null = null;

  constructor() {
    // HU-13 · A removed row takes its focused button with it: focus the next row's "Quitar", or the title after the last one.
    afterRenderEffect(() => {
      const items = this.store.cartItems();
      const leaving = this.leaving;
      if (!leaving || items.some(item => item.productId === leaving.productId)) return;
      this.leaving = null;
      const next = this.host.querySelectorAll<HTMLElement>('[aria-label^="Quitar "]')[leaving.index];
      (next ?? this.heading().nativeElement).focus();
    });
  }

  protected markLeaving(productId: number, index: number): void {
    this.leaving = { productId, index };
  }
}
