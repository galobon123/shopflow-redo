import { ChangeDetectionStrategy, Component, ElementRef, afterRenderEffect, computed, input, output } from '@angular/core';
import { CartItem } from '../../core/models/cart.model';
import { ArsPipe, SfAlert, SfAlertAction, SfBadge, SfButton, SfQtyStepper } from '../../ui';
import { EcommerceStore } from '../../ecommerce-store';
import { inject } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatIconButton } from '@angular/material/button';

@Component({
  selector: 'app-show-cart-item',
  imports: [ArsPipe, SfQtyStepper, SfAlert, SfAlertAction, SfBadge, SfButton, MatIcon, MatIconButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './show-cart-item.html',
  styleUrl: './show-cart-item.css',
})
export class ShowCartItem {
  item = input.required<CartItem>();
  store =inject(EcommerceStore);

  total = computed(() => this.item().productPrice * this.item().quantity);

  /** HU-13 · The stock dropped below what's in the cart: said plainly, never trimmed behind the customer's back. */
  overStock = computed(() => this.item().available && this.item().quantity > this.item().stock);

  /** The row is about to go (removed or moved to favourites): the list decides where the focus lands. */
  readonly leaving = output<void>();

  private readonly host: HTMLElement = inject(ElementRef).nativeElement;
  private focusStepper = false;

  constructor() {
    // HU-13 · "Dejar N" is replaced by the selector: the focus follows to it instead of dropping to the page.
    afterRenderEffect(() => {
      if (this.overStock() || !this.focusStepper) return;
      this.focusStepper = false;
      this.host.querySelector<HTMLElement>('[aria-label="Restar uno"]')?.focus();
    });
  }

  protected keepStock(): void {
    this.focusStepper = true;
    this.store.updateCartQty(this.item().productId, this.item().stock);
  }

  protected remove(): void {
    this.leaving.emit();
    this.store.removeFromCart(this.item().productId);
  }

  protected moveToWishlist(): void {
    this.leaving.emit();
    this.store.moveToWishlist(this.item());
  }
}
