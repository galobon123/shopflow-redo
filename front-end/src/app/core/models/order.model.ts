import { AddressResponse } from './address.model';

export interface OrderRequest {
    addressId: number;
}

export interface OrderResponse {
    id: number;
    createdAt: string;
    total: number;
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
