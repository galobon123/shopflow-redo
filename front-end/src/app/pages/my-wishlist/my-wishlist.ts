import { Component, inject } from '@angular/core';
import { BackButton } from "../../components/back-button/back-button";
import { EcommerceStore } from '../../ecommerce-store';
import { ProductCard } from "../../components/product-card/product-card";
import { LucideAngularModule, Trash2 } from 'lucide-angular';
import { SfButton, SfIconButton } from '../../ui';
import { EmptyWishlist } from "./empty-wishlist/empty-wishlist";
import { ProductResponse } from '../../core/models/product.model';
import { WishListResponse } from '../../core/models/wishlist.model';

@Component({
  selector: 'app-my-wishlist',
  imports: [BackButton, ProductCard, SfButton, SfIconButton, LucideAngularModule, EmptyWishlist],
  templateUrl: './my-wishlist.html',
  styleUrl: './my-wishlist.css',
})
export class MyWishlist {
  store = inject(EcommerceStore);

  protected readonly trashIcon = Trash2;

  mapToProduct(item: WishListResponse): ProductResponse {
    return {
      id: item.productId,
      name: item.productName,
      description: '',
      price: item.productPrice,
      stock: item.productStock,
      categoryName: '',
      active: true,
      createdAt: item.createdAt,
      imageUrl: item.productImageUrl,
    };
  }
}
