import { ChangeDetectionStrategy, Component, inject, viewChild } from '@angular/core';
import { BackButton } from "../../components/back-button/back-button";
import { ShippingForm } from './shipping-form/shipping-form';
import { SummarizeOrder } from '../../components/summarize-order/summarize-order';
import { EcommerceStore } from '../../ecommerce-store';
import { AuthStore } from '../../auth-store';
import { RouterLink } from '@angular/router';
import { ArsPipe, SfAlert, SfAlertAction, SfBadge, SfButton } from '../../ui';

@Component({
  selector: 'app-checkout',
  imports: [BackButton, ShippingForm, SummarizeOrder, RouterLink, ArsPipe, SfAlert, SfAlertAction, SfBadge, SfButton],
  templateUrl: './checkout.html',
  styleUrl: './checkout.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Checkout {
  store = inject(EcommerceStore);
  /** HU-31 · Buying waits for the email confirmation; the backend refuses it too. */
  authStore = inject(AuthStore);
  private shippingForm = viewChild.required(ShippingForm);

  /**
   * HU-13 · Same as the cart page: the checkout decides on a fresh cart, not on the one read at login.
   * HU-16 · "Mis direcciones" too: the last used comes chosen.
   * HU-17 · And a new Idempotency-Key for the purchase about to be confirmed.
   */
  constructor() {
    this.store.startCheckout();
    this.store.loadCart();
    this.store.loadAddresses();
  }

  placeOrder(): void {
    if (this.store.loading() || !this.authStore.emailVerified() || this.store.cartBlocked()) return;
    const shipTo = this.shippingForm().shipTo();
    if (shipTo === null) return;
    this.store.placeOrder(shipTo);
  }
}
