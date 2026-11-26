import { AddressResponse } from './address.model';

export interface OrderRequest {
    addressId: number;
}

export interface OrderResponse {
    id: number;
    createdAt: string;
    total: number;
    /** HU-17 · Born PENDIENTE_PAGO; HU-18 moves it on. */
    status: 'PENDIENTE_PAGO' | 'PAGADA' | 'ENVIADA' | 'ENTREGADA' | 'CANCELADA';
    /** HU-17 · Until when it holds the stock without being paid. */
    expiresAt: string;
    address: AddressResponse;
    items: OrderItem[];
}

export interface OrderItem {
    productId: number;
    productName: string;
    quantity: number;
    unitPrice: number;
    subtotal: number;
}
