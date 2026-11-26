import { ChangeDetectionStrategy, Component, ElementRef, afterRenderEffect, computed, effect, inject, input, signal, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { rxResource } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { PackageX } from 'lucide-angular';
import { BackButton } from '../../components/back-button/back-button';
import { OrderReceipt } from '../../components/order-receipt/order-receipt';
import { OrderStatusBadge, orderDate, orderDateTime } from '../../components/order-status/order-status';
import { PayButton } from '../../components/pay-button/pay-button';
import { orderIdOf, paymentExpired } from '../../core/models/order.model';
import { OrderService } from '../../core/services/order.service';
import { errorMessage } from '../../core/util/error-message';
import { SfAlert, SfButton, SfEmptyState, SfErrorState, SfSkeleton } from '../../ui';

/**
 * HU-19 · One order of "Mis pedidos" at /account/orders/:id: date, status, products, total and address; until when
 * it can be paid while it waits for the payment, and HU-18's "Pagar con Mercado Pago". Someone else's order, a
 * missing one and a made-up id all read "Pedido no encontrado", so nobody learns which orders exist.
 */
@Component({
  selector: 'app-order-detail',
  imports: [
    RouterLink, BackButton, OrderReceipt, OrderStatusBadge, PayButton, SfAlert, SfButton, SfEmptyState, SfErrorState,
    SfSkeleton,
  ],
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

  /** HU-18 · The pay button went away with a 409: the focus goes to the title of the order read again. */
  private readonly heading = viewChild<ElementRef<HTMLElement>>('heading');
  private readonly focusHeading = signal(false);

  protected readonly icons = { notFound: PackageX };
  protected readonly orderDate = orderDate;
  protected readonly orderDateTime = orderDateTime;
  protected readonly paymentExpired = paymentExpired;

  protected refusedPayment(): void {
    this.focusHeading.set(true);
    this.order.reload();
  }

  constructor() {
    // After rendering: by then the title is the one of the order read again, not the one going away.
    afterRenderEffect(() => {
      const heading = this.heading();
      if (this.focusHeading() && heading && !this.order.isLoading()) {
        heading.nativeElement.focus();
        this.focusHeading.set(false);
      }
    });
    effect(() => {
      const id = this.detail()?.id;
      this.title.setTitle(id ? `Pedido #${id} · ShopFlow` : this.notFound() ? 'Pedido no encontrado · ShopFlow' : 'Pedido · ShopFlow');
    });
  }
}
