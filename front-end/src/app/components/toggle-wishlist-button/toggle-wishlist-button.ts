import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { Heart, LucideAngularModule } from 'lucide-angular';
import { EcommerceStore } from '../../ecommerce-store';
import { ProductResponse } from '../../core/models/product.model';
import { SfIconButton } from '../../ui';

@Component({
  selector: 'app-toggle-wishlist-button',
  imports: [SfIconButton, LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './toggle-wishlist-button.html',
  styleUrl: './toggle-wishlist-button.css',
})
export class ToggleWishlistButton {
  readonly product = input.required<ProductResponse>();

  readonly store = inject(EcommerceStore);

  protected readonly heartIcon = Heart;

  readonly isInWishlist = computed(() =>
    this.store.wishListItems().some(item => item.productId === this.product().id)
  );

  /** Waiting for the server: the heart pulses and ignores taps until it answers. */
  readonly saving = computed(() => this.store.wishListSaving().includes(this.product().id));

  toggleWishlist() {
    if (this.saving()) return;
    const product = this.product();
    if (this.isInWishlist()) {
      this.store.removeFromWishList(product.id);
    } else {
      this.store.addToWishList(product);
    }
  }
}
