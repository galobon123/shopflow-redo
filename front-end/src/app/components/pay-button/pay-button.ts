import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { paymentErrorMessage, PaymentService } from '../../core/services/payment.service';
import { onPageRestored } from '../../core/util/page-restored';
import { SfAlert, SfButton } from '../../ui';

/**
 * HU-18 · "Pagar con Mercado Pago" for an order waiting for its payment: asks the store for the order's checkout and
 * goes there. The button waits (aria-busy, and ignores more clicks) until it leaves; if it can't, it says why under
 * it. Never disabled while waiting: a disabled button drops its focus to the page (WCAG 2.4.3).
 * A 409 means the order can't be paid any more: `refused` lets the page read it again, show why and take the focus.
 * Back from Mercado Pago (the page restored from the browser's cache) it stops waiting, and `restored` lets the page
 * read the order again: it may have been paid meanwhile. Paying again reuses the order's checkout.
 */
@Component({
  selector: 'app-pay-button',
  imports: [SfAlert, SfButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'grid justify-items-start gap-3' },
  template: `
    <button sfButton="primary" size="lg" type="button" [loading]="paying()"
      [attr.aria-disabled]="paying() || null" (click)="pay()">
      {{ label() }}
    </button>
    @if (error(); as message) {
      <sf-alert>{{ message }}</sf-alert>
    }
  `,
})
export class PayButton {
  private readonly payments = inject(PaymentService);

  readonly orderId = input.required<number>();
  readonly label = input('Pagar con Mercado Pago');
  readonly refused = output<void>();
  readonly restored = output<void>();

  protected readonly paying = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    onPageRestored(() => {
      if (!this.paying()) return;
      this.paying.set(false);
      this.restored.emit();
    });
  }

  pay(): void {
    if (this.paying()) return;
    this.paying.set(true);
    this.error.set(null);
    this.payments.checkout(this.orderId()).subscribe({
      // Stays waiting: the page is leaving for Mercado Pago.
      next: ({ checkoutUrl }) => this.payments.goTo(checkoutUrl),
      error: (err) => {
        this.paying.set(false);
        this.error.set(paymentErrorMessage(err));
        if (err instanceof HttpErrorResponse && err.status === 409) this.refused.emit();
      },
    });
  }
}
