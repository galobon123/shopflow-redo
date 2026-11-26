package com.Proyect.Ecommerce.dto.cart;

import java.util.List;

/** HU-12 · The account's cart after taking in the guest's, and one notice per quantity trimmed to the stock. */
public record CartMergeDTO(List<CartItemDTO> items, List<String> notices) {
}
