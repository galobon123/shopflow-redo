import { Component, computed, input } from '@angular/core';
import { CartItem } from '../../core/models/cart.model';
import { DecimalPipe } from '@angular/common';
import { SfQtyStepper } from '../../ui';
import { EcommerceStore } from '../../ecommerce-store';
import { inject } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatIconButton } from '@angular/material/button';

@Component({
  selector: 'app-show-cart-item',
  imports: [DecimalPipe, SfQtyStepper, MatIcon, MatIconButton],
  templateUrl: './show-cart-item.html',
  styleUrl: './show-cart-item.css',
})
export class ShowCartItem {
  item = input.required<CartItem>();
  store =inject(EcommerceStore);

  total = computed(() => (this.item().productPrice * this.item().quantity).toFixed(2));
}
