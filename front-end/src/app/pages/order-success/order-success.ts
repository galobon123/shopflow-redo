import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { CircleCheck, LucideAngularModule, PackageX } from 'lucide-angular';
import { OrderReceipt } from '../../components/order-receipt/order-receipt';
import { OrderStatusBadge, orderDateTime } from '../../components/order-status/order-status';
import { orderIdOf, paymentExpired } from '../../core/models/order.model';
import { OrderService } from '../../core/services/order.service';
import { errorMessage } from '../../core/util/error-message';
import { SfAlert, SfButton, SfEmptyState, SfErrorState, SfSkeleton } from '../../ui';

/**
 * HU-19 · The receipt after buying, at /order-success/:id. It reads the order by its id, so a reload still shows it.
 * HU-18 · The order waits for its payment here: this is where paying in Mercado Pago will start.
 */
@Component({
  selector: 'app-order-success',
  imports: [
    RouterLink, LucideAngularModule, OrderReceipt, OrderStatusBadge, SfAlert, SfButton, SfEmptyState, SfErrorState,
    SfSkeleton,
  ],
  templateUrl: './order-success.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderSuccess {
  private readonly orderService = inject(OrderService);

  /** Path param. */
  readonly id = input.required<string>();

  private readonly orderId = computed(() => orderIdOf(this.id()));

  protected readonly order = rxResource({
    params: () => this.orderId() ?? undefined,
    stream: ({ params: id }) => this.orderService.getById(id),
  });

  protected readonly detail = computed(() => (this.order.hasValue() ? this.order.value() : undefined));
  protected readonly notFound = computed(() => {
    const error = this.order.error();
    return this.orderId() === null || (error instanceof HttpErrorResponse && error.status === 404);
  });
  protected readonly error = computed(() =>
    this.order.error() && !this.notFound() ? errorMessage(this.order.error()) : null,
  );

  protected readonly icons = { done: CircleCheck, notFound: PackageX };
  protected readonly orderDateTime = orderDateTime;
  protected readonly paymentExpired = paymentExpired;
}
