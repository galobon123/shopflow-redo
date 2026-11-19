import { Component, inject } from '@angular/core';
import { ViewPanel } from '../../../directives/view-panel';
import { ShowCartItem } from '../../show-cart-item/show-cart-item';
import { EcommerceStore } from '../../../ecommerce-store';

@Component({
  selector: 'app-list-cart-items',
  imports: [ViewPanel, ShowCartItem],
  templateUrl: './list-cart-items.html',
  styleUrl: './list-cart-items.css',
})
export class ListCartItems {
  store = inject(EcommerceStore);
}
