import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { EcommerceStore } from '../../ecommerce-store';
import { ArsPipe } from '../../ui';

/**
 * Order summary (UI Kit › OrderSummary).
 * HU-17 · Prices already include IVA, so the total is what the products add up to; the backend works out the
 * order's own total again when it's confirmed.
 */
@Component({
  selector: 'app-summarize-order',
  imports: [ArsPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './summarize-order.html',
  styleUrl: './summarize-order.css',
})
export class SummarizeOrder {
  store = inject(EcommerceStore);

  /** HU-13 · From the store, which leaves unavailable items out. */
  itemCount = this.store.cartCount;

  total = this.store.cartTotal;
}
