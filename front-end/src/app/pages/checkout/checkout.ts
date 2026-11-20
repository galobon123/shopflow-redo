import { Component, inject, ViewChild } from '@angular/core';
import { BackButton } from "../../components/back-button/back-button";
import { DecimalPipe } from '@angular/common';
import { PaymentForm } from './payment-form/payment-form';
import { ShippingForm } from './shipping-form/shipping-form';
import { SummarizeOrder } from '../../components/summarize-order/summarize-order';
import { EcommerceStore } from '../../ecommerce-store';
import { MatButton } from '@angular/material/button';
import { AuthStore } from '../../auth-store';
import { SfAlert } from '../../ui';

@Component({
  selector: 'app-checkout',
  imports: [BackButton, ShippingForm, PaymentForm, SummarizeOrder, MatButton, DecimalPipe, SfAlert],
  templateUrl: './checkout.html',
  styleUrl: './checkout.css',
})
export class Checkout {
  store = inject(EcommerceStore);
  /** HU-31 · Buying waits for the email confirmation; the backend refuses it too. */
  authStore = inject(AuthStore);
  @ViewChild(ShippingForm) shippingForm!: ShippingForm;

  placeOrder(): void {
    if (!this.authStore.emailVerified()) return;
    if (this.shippingForm.form.invalid) {
      this.shippingForm.form.markAllAsTouched();
      return;
    }
    this.store.placeOrder(this.shippingForm.form.value);
  }
}
