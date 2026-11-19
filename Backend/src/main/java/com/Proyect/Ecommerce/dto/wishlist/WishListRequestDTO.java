package com.Proyect.Ecommerce.dto.wishlist;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WishListRequestDTO {

    @NotNull(message = "Falta el producto")
    private Long productId;
}
