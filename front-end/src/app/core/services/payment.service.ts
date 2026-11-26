import { DOCUMENT, inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PaymentCheckout } from '../models/order.model';
import { errorMessage, problemOf } from '../util/error-message';

/**
 * HU-18 · Why paying didn't start. Unlike other 5xx, the payment's 503 is written for the customer ("El pago no está
 * disponible…", "No pudimos conectar con Mercado Pago…"), so it's shown as it comes.
 */
export function paymentErrorMessage(err: unknown): string {
  const detail = problemOf(err)?.detail;
  return err instanceof HttpErrorResponse && err.status === 503 && detail ? detail : errorMessage(err);
}

/** HU-18 · Paying an order in Mercado Pago (Checkout Pro): the store asks where, and the browser goes there. */
@Injectable({
  providedIn: 'root'
})
export class PaymentService {
  private readonly http = inject(HttpClient);
  private readonly document = inject(DOCUMENT);

  /**
   * Mercado Pago's checkout for one of the customer's orders: always the same one for the same order. 409 once it
   * can't be paid any more (paid, cancelled or out of time), 503 when paying is off or Mercado Pago is down.
   */
  checkout(orderId: number): Observable<PaymentCheckout> {
    return this.http.post<PaymentCheckout>(`${environment.apiUrl}/orders/${orderId}/payment`, null);
  }

  /** Leaves the store for Mercado Pago; it comes back to /checkout/result/:id/:outcome. */
  goTo(url: string): void {
    this.document.location.href = url;
  }
}
