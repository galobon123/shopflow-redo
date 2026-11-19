import { Component, inject, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { DecimalPipe, DatePipe } from '@angular/common';
import { OrderResponse } from '../../core/models/order.model';
import { ViewPanel } from '../../directives/view-panel';
import { MatIcon } from '@angular/material/icon';
import { MatButton } from '@angular/material/button';

@Component({
  selector: 'app-order-success',
  imports: [RouterLink, DecimalPipe, DatePipe, ViewPanel, MatIcon, MatButton],
  templateUrl: './order-success.html',
  styleUrl: './order-success.css',
})
export class OrderSuccess implements OnInit {
  private router = inject(Router);
  order: OrderResponse | null = null;

  ngOnInit(): void {
    const state = history.state;
    if (state?.['order']) {
      this.order = state['order'];
    }
  }
}
