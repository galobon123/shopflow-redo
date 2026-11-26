import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule, Package } from 'lucide-angular';
import { OrderResponse, orderUnits } from '../../core/models/order.model';
import { ArsPipe, SfButton } from '../../ui';

/**
 * HU-19 · What an order has: its products (photo, name linking to the product, quantity, unit price and subtotal),
 * the total and where it goes. Shared by the order page in "Mis pedidos" and the receipt after buying.
 * "Calificar" next to each product the customer can still review (the API says which: delivered orders only).
 */
@Component({
  selector: 'app-order-receipt',
  imports: [RouterLink, LucideAngularModule, ArsPipe, SfButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="grid grid-cols-1 gap-6 lg:grid-cols-5">
      <section class="lg:col-span-3" aria-labelledby="receipt-items">
        <h2 id="receipt-items" class="text-title mb-4">Productos</h2>
        <ul class="grid gap-4">
          @for (item of order().items; track item.productId) {
            <li class="flex items-start gap-4" data-item>
              @if (item.imageUrl) {
                <img [src]="item.imageUrl" alt="" class="size-16 shrink-0 rounded object-cover sm:size-20" />
              } @else {
                <span class="grid size-16 shrink-0 place-items-center rounded bg-sunken text-ink-muted sm:size-20" aria-hidden="true">
                  <lucide-icon [img]="packageIcon" />
                </span>
              }
              <div class="grid min-w-0 flex-1 gap-1">
                <a class="sf-link break-words font-semibold" [routerLink]="['/product', item.productId]">{{ item.productName }}</a>
                <p class="text-body-s text-ink-muted">{{ item.quantity }} × {{ item.unitPrice | ars }}</p>
                @if (item.canReview) {
                  <!-- HU-30 · The review form is in the product page's reviews panel. -->
                  <a sfButton="secondary" size="sm" class="mt-1 justify-self-start" [routerLink]="['/product', item.productId]"
                    fragment="reviews">Calificar<span class="sr-only"> {{ item.productName }}</span></a>
                }
              </div>
              <span class="shrink-0 font-semibold tabular-nums">{{ item.subtotal | ars }}</span>
            </li>
          }
        </ul>
      </section>

      <div class="grid content-start gap-6 lg:col-span-2">
        <aside class="sf-summary max-w-none" aria-label="Total del pedido">
          <h2>Resumen</h2>
          <dl>
            <div><dt>Productos ({{ units() }})</dt><dd>{{ order().total | ars }}</dd></div>
          </dl>
          <div class="sf-summary__total"><span>Total</span><strong>{{ order().total | ars }}</strong></div>
          <p class="sf-summary__note">IVA incluido</p>
        </aside>

        <section aria-labelledby="receipt-address" class="grid gap-1">
          <h2 id="receipt-address" class="text-title mb-1">Envío a</h2>
          <p class="font-semibold">{{ order().address.firstName }} {{ order().address.lastName }}</p>
          <p class="text-body-s text-ink-muted">{{ order().address.address }}</p>
          <p class="text-body-s text-ink-muted">
            {{ order().address.cityName }}, {{ order().address.stateName }} · CP {{ order().address.zipCode }}
          </p>
        </section>
      </div>
    </div>
  `,
})
export class OrderReceipt {
  readonly order = input.required<OrderResponse>();

  protected readonly units = computed(() => orderUnits(this.order()));
  protected readonly packageIcon = Package;
}
