import { ChangeDetectionStrategy, Component, ElementRef, afterRenderEffect, computed, inject, input, signal, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { EMPTY, defer, expand, switchMap, tap, timer } from 'rxjs';
import { CircleCheck, CircleX, Clock, LucideAngularModule, PackageX } from 'lucide-angular';
import { OrderReceipt } from '../../components/order-receipt/order-receipt';
import { orderDateTime } from '../../components/order-status/order-status';
import { PayButton } from '../../components/pay-button/pay-button';
import { OrderResponse, PaymentOutcome, orderIdOf, paymentExpired } from '../../core/models/order.model';
import { OrderService } from '../../core/services/order.service';
import { errorMessage } from '../../core/util/error-message';
import { SfButton, SfEmptyState, SfErrorState, SfSkeleton } from '../../ui';

/** What the page says: the order's real status, with Mercado Pago's outcome only to tell apart the waiting ones. */
type View = 'paid' | 'confirming' | 'unconfirmed' | 'pending' | 'rejected' | 'expired' | 'cancelled';

/**
 * HU-18 · Where Mercado Pago sends the customer back: /checkout/result/:id/:outcome (approved, pending or rejected,
 * the preference's back_urls). The URL is never trusted: the page reads the order. Mercado Pago's webhook may come a
 * few seconds after the customer, so while the order still waits after an approved (or pending) payment the page
 * says "Estamos confirmando tu pago…" and reads it again every few seconds, for a while.
 */
@Component({
  selector: 'app-payment-result',
  imports: [RouterLink, LucideAngularModule, OrderReceipt, PayButton, SfButton, SfEmptyState, SfErrorState, SfSkeleton],
  templateUrl: './payment-result.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaymentResult {
  /** Every 3 seconds, 10 times: half a minute. */
  static readonly POLL_EVERY_MS = 3000;
  static readonly POLL_TIMES = 10;

  private readonly orderService = inject(OrderService);

  /** Path params. */
  readonly id = input.required<string>();
  readonly outcome = input.required<string>();

  private readonly orderId = computed(() => orderIdOf(this.id()));
  /** Times the order was read for the current id. */
  private readonly reads = signal(0);

  protected readonly order = rxResource({
    params: () => this.orderId() ?? undefined,
    stream: ({ params: id }) => defer(() => {
      this.reads.set(0);
      return this.orderService.getById(id).pipe(
        expand((order, i) => this.waitsForWebhook(order) && i < PaymentResult.POLL_TIMES - 1
          ? timer(PaymentResult.POLL_EVERY_MS).pipe(switchMap(() => this.orderService.getById(id)))
          : EMPTY),
        tap(() => this.reads.update((n) => n + 1)),
      );
    }),
  });

  protected readonly detail = computed(() => (this.order.hasValue() ? this.order.value() : undefined));
  protected readonly notFound = computed(() => {
    const error = this.order.error();
    return this.orderId() === null || (error instanceof HttpErrorResponse && error.status === 404);
  });
  protected readonly error = computed(() =>
    this.order.error() && !this.notFound() ? errorMessage(this.order.error()) : null,
  );

  protected readonly view = computed<View | null>(() => {
    const order = this.detail();
    if (!order) return null;
    if (order.status === 'CANCELADA') return 'cancelled';
    if (order.status !== 'PENDIENTE_PAGO') return 'paid';
    if (paymentExpired(order)) return 'expired';
    switch (this.paymentOutcome()) {
      case 'approved': return this.reads() < PaymentResult.POLL_TIMES ? 'confirming' : 'unconfirmed';
      case 'rejected': return 'rejected';
      default: return 'pending';
    }
  });

  /** The retry went away with a 409: the focus goes to the title of what the page says now. */
  private readonly heading = viewChild<ElementRef<HTMLElement>>('heading');
  private readonly focusHeading = signal(false);

  constructor() {
    // After rendering: by then the title is the one of the order read again, not the one going away.
    afterRenderEffect(() => {
      const heading = this.heading();
      if (this.focusHeading() && heading && !this.order.isLoading()) {
        heading.nativeElement.focus();
        this.focusHeading.set(false);
      }
    });
  }

  protected refusedPayment(): void {
    this.focusHeading.set(true);
    this.order.reload();
  }

  protected readonly icons = { paid: CircleCheck, rejected: CircleX, waiting: Clock, gone: PackageX };
  protected readonly orderDateTime = orderDateTime;

  /** Anything else in the URL reads as "pending": the order's status decides anyway. */
  protected readonly paymentOutcome = computed<PaymentOutcome>(() => {
    const outcome = this.outcome();
    return outcome === 'approved' || outcome === 'rejected' ? outcome : 'pending';
  });

  private waitsForWebhook(order: OrderResponse): boolean {
    return order.status === 'PENDIENTE_PAGO' && !paymentExpired(order) && this.paymentOutcome() !== 'rejected';
  }
}
