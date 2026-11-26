import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { PackageOpen } from 'lucide-angular';
import { OrderStatusBadge, orderDate, unitsText } from '../../components/order-status/order-status';
import { orderUnits } from '../../core/models/order.model';
import { OrderService } from '../../core/services/order.service';
import { errorMessage } from '../../core/util/error-message';
import { ArsPipe, SfButton, SfEmptyState, SfErrorState, SfPagination, SfSkeleton } from '../../ui';

/**
 * HU-19 · "Mis pedidos" at /account/orders?page=2: the customer's orders, newest first, each with its date,
 * number, how many products, total and status, and a link to its detail.
 */
@Component({
  selector: 'app-my-orders',
  imports: [RouterLink, OrderStatusBadge, ArsPipe, SfButton, SfEmptyState, SfErrorState, SfPagination, SfSkeleton],
  templateUrl: './my-orders.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MyOrders {
  private readonly orderService = inject(OrderService);

  /** Query param, 1-based in the URL; the API counts from 0. */
  readonly page = input<string>();

  protected readonly pageNumber = computed(() => Math.max(1, Number.parseInt(this.page() ?? '1', 10) || 1));

  protected readonly orders = rxResource({
    params: () => this.pageNumber() - 1,
    stream: ({ params: page }) => this.orderService.getMyOrders(page),
  });

  protected readonly data = computed(() => (this.orders.hasValue() ? this.orders.value() : undefined));
  protected readonly error = computed(() => (this.orders.error() ? errorMessage(this.orders.error()) : null));

  protected readonly icons = { empty: PackageOpen };
  protected readonly skeletons = [0, 1, 2];
  protected readonly orderDate = orderDate;
  protected readonly orderUnits = orderUnits;
  protected readonly unitsText = unitsText;
}
