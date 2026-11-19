import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Heart } from 'lucide-angular';
import { SfButton, SfEmptyState } from '../../../ui';

@Component({
  selector: 'app-empty-wishlist',
  imports: [SfEmptyState, SfButton, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './empty-wishlist.html',
  styleUrl: './empty-wishlist.css',
})
export class EmptyWishlist {
  protected readonly heartIcon = Heart;
}
