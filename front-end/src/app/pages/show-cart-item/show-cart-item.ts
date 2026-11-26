import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { CartItem } from '../../core/models/cart.model';
import { ArsPipe, SfBadge, SfQtyStepper } from '../../ui';
import { EcommerceStore } from '../../ecommerce-store';
import { inject } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatIconButton } from '@angular/material/button';

@Component({
  selector: 'app-show-cart-item',
  imports: [ArsPipe, SfQtyStepper, SfBadge, MatIcon, MatIconButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './show-cart-item.html',
  styleUrl: './show-cart-item.css',
})
export class ShowCartItem {
  item = input.required<CartItem>();
  store =inject(EcommerceStore);

  total = computed(() => this.item().productPrice * this.item().quantity);

  /** The row is about to go (removed or moved to favourites): the list decides where the focus lands. */
  readonly leaving = output<void>();

  protected remove(): void {
    this.leaving.emit();
    this.store.removeFromCart(this.item().productId);
  }

  protected moveToWishlist(): void {
    this.leaving.emit();
    this.store.moveToWishlist(this.item());
  }
}
