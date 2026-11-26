import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { rxResource } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { PackageX } from 'lucide-angular';
import { BackButton } from '../../components/back-button/back-button';
import { OrderReceipt } from '../../components/order-receipt/order-receipt';
import { OrderStatusBadge, orderDate, orderDateTime } from '../../components/order-status/order-status';
import { orderIdOf, paymentExpired } from '../../core/models/order.model';
import { OrderService } from '../../core/services/order.service';
import { errorMessage } from '../../core/util/error-message';
import { SfAlert, SfButton, SfEmptyState, SfErrorState, SfSkeleton } from '../../ui';

/**
 * HU-19 · One order of "Mis pedidos" at /account/orders/:id: date, status, products, total and address; until when
 * it can be paid while it waits for the payment (HU-18 adds the button). Someone else's order, a missing one and a
 * made-up id all read "Pedido no encontrado", so nobody learns which orders exist.
 */
@Component({
  selector: 'app-order-detail',
  imports: [RouterLink, BackButton, OrderReceipt, OrderStatusBadge, SfAlert, SfButton, SfEmptyState, SfErrorState, SfSkeleton],
  templateUrl: './order-detail.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrderDetail {
  private readonly orderService = inject(OrderService);
  private readonly title = inject(Title);

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

  protected readonly icons = { notFound: PackageX };
  protected readonly orderDate = orderDate;
  protected readonly orderDateTime = orderDateTime;
  protected readonly paymentExpired = paymentExpired;

  constructor() {
    effect(() => {
      const id = this.detail()?.id;
      this.title.setTitle(id ? `Pedido #${id} · ShopFlow` : this.notFound() ? 'Pedido no encontrado · ShopFlow' : 'Pedido · ShopFlow');
    });
  }
}
