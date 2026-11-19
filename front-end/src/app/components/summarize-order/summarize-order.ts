import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { EcommerceStore } from '../../ecommerce-store';
import { ArsPipe } from '../../ui';

/**
 * Order summary (UI Kit › OrderSummary).
 * shortcut: totals are still computed here; HU-17 moves them to the backend (and Argentine prices already include IVA).
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

  itemCount = computed(() => this.store.cartItems().reduce((acc, item) => acc + item.quantity, 0));

  subtotal = computed(() => this.store.cartItems().reduce((acc, item) => acc + item.productPrice * item.quantity, 0));

  tax = computed(() => this.subtotal() * 0.21);

  total = computed(() => this.subtotal() + this.tax());
}
