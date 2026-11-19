import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { LucideAngularModule, ShoppingBag } from 'lucide-angular';
import { ProductResponse } from '../../core/models/product.model';
import { EcommerceStore } from '../../ecommerce-store';
import { ArsPipe, SfButton, SfStockBadge } from '../../ui';

/** Product card (UI Kit › ProductCard). The top-right action (favourite or remove) is projected. */
@Component({
  selector: 'app-product-card',
  imports: [ArsPipe, SfButton, SfStockBadge, LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-card.html',
  styleUrl: './product-card.css',
  host: { class: 'block' },
})
export class ProductCard {
  readonly store = inject(EcommerceStore);

  readonly product = input.required<ProductResponse>();

  /** When the photo fails, the well shows the product name instead of a stock image. */
  protected readonly imageFailed = signal(false);
  protected readonly bagIcon = ShoppingBag;
}
