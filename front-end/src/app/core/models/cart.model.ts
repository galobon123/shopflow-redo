export interface CartItem {
  productId: number;
  productName: string;
  productPrice: number;
  productImageUrl: string;
  quantity: number;
  /** HU-13 · Units left now. The quantity is never trimmed to it, so it can be higher. */
  stock: number;
  /** HU-13 · False when the product was deactivated or ran out; it stays listed until removed. */
  available: boolean;
}

/** HU-12 · The account's cart after the guest's joined it; a notice per quantity trimmed to the stock. */
export interface CartMerge {
  items: CartItem[];
  notices: string[];
}
