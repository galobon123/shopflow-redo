import { AddressResponse } from './address.model';

export interface OrderRequest {
    addressId: number;
}

/** HU-17 · Born PENDIENTE_PAGO; HU-18 moves it on. */
export type OrderStatus = 'PENDIENTE_PAGO' | 'PAGADA' | 'ENVIADA' | 'ENTREGADA' | 'CANCELADA';

export interface OrderResponse {
    id: number;
    /** HU-19 · An instant (UTC); shown in the store's time zone. */
    createdAt: string;
    total: number;
    status: OrderStatus;
    /** HU-17 · Until when it holds the stock without being paid. */
    expiresAt: string | null;
    address: AddressResponse;
    items: OrderItem[];
}

export interface OrderItem {
    productId: number;
    productName: string;
    /** HU-19 · The product's cover photo; null when it has none. */
    imageUrl: string | null;
    quantity: number;
    unitPrice: number;
    subtotal: number;
    /** HU-19 · "Calificar": delivered, still for sale and not reviewed yet. */
    canReview: boolean;
}

/** HU-18 · Where the customer pays the order in Mercado Pago. */
export interface PaymentCheckout {
    checkoutUrl: string;
}

/** HU-18 · How Mercado Pago sent the customer back (the result page's URL). A hint: the order's status is the truth. */
export type PaymentOutcome = 'approved' | 'pending' | 'rejected';

/** HU-19 · How a customer reads each status. */
export const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
    PENDIENTE_PAGO: 'Esperando el pago',
    PAGADA: 'Pagado',
    ENVIADA: 'Enviado',
    ENTREGADA: 'Entregado',
    CANCELADA: 'Cancelado',
};

/** HU-19 · Units bought: two of one product and one of another are 3. */
export function orderUnits(order: OrderResponse): number {
    return order.items.reduce((units, item) => units + item.quantity, 0);
}

/** HU-19 · An order id from the URL, or null: anything but a positive whole number can't be one and needs no request. */
export function orderIdOf(id: string | undefined): number | null {
    const n = Number(id);
    return Number.isInteger(n) && n > 0 ? n : null;
}

/** HU-19 · Waiting for a payment whose time is up (HU-18's job cancels it soon). */
export function paymentExpired(order: OrderResponse, now = new Date()): boolean {
    return order.status === 'PENDIENTE_PAGO' && order.expiresAt !== null && new Date(order.expiresAt) <= now;
}
