import { ChangeDetectionStrategy, Component, ElementRef, Injector, afterNextRender, afterRenderEffect, inject, signal, viewChild } from '@angular/core';
import { BackButton } from "../../components/back-button/back-button";
import { EcommerceStore } from '../../ecommerce-store';
import { ProductCard } from "../../components/product-card/product-card";
import { LucideAngularModule, Trash2 } from 'lucide-angular';
import { SfButton, SfErrorState, SfIconButton, SfSkeleton } from '../../ui';
import { EmptyWishlist } from "./empty-wishlist/empty-wishlist";
import { ProductResponse } from '../../core/models/product.model';
import { WishListResponse } from '../../core/models/wishlist.model';

@Component({
  selector: 'app-my-wishlist',
  imports: [BackButton, ProductCard, SfButton, SfIconButton, SfErrorState, SfSkeleton, LucideAngularModule, EmptyWishlist],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './my-wishlist.html',
  styleUrl: './my-wishlist.css',
})
export class MyWishlist {
  store = inject(EcommerceStore);

  protected readonly trashIcon = Trash2;

  /** HU-14 · "Vaciar favoritos" was pressed once: it waits for "Sí, vaciar". */
  readonly confirmingClear = signal(false);

  private readonly host: HTMLElement = inject(ElementRef).nativeElement;
  private readonly injector = inject(Injector);
  private readonly askClear = viewChild('askClear', { read: ElementRef<HTMLButtonElement> });
  private readonly confirmClear = viewChild('confirmClear', { read: ElementRef<HTMLButtonElement> });
  /** HU-14 · Taken out of the list before the server answered: if it comes back, so does the focus. */
  private removing: number | null = null;

  constructor() {
    // HU-14 · Keyboard focus never falls to the page (WCAG 2.4.3) when the list changes under it.
    let hadItems = false;
    afterRenderEffect(() => {
      const items = this.store.wishListItems();
      const saving = this.store.wishListSaving();
      if (this.removing !== null && !saving.includes(this.removing)) {
        const failed = items.some(item => item.productId === this.removing);
        if (failed) this.trashOf(this.removing)?.focus();
        this.removing = null;
      }
      // Emptied (all at once or the last one): the focus goes to the empty state's title.
      const hasItems = items.length > 0;
      if (hadItems && !hasItems && (!document.activeElement || document.activeElement === document.body)) {
        const title = this.host.querySelector<HTMLElement>('sf-empty-state h2');
        title?.setAttribute('tabindex', '-1');
        title?.focus();
      }
      hadItems = hasItems;
    });
  }

  askToClear() {
    this.confirmingClear.set(true);
    afterNextRender(() => this.confirmClear()?.nativeElement.focus(), { injector: this.injector });
  }

  cancelClear() {
    if (this.store.wishListClearing()) return;
    this.confirmingClear.set(false);
    afterNextRender(() => this.askClear()?.nativeElement.focus(), { injector: this.injector });
  }

  /** The focus moves to the next card's trash (or the previous one, if it was the last card). */
  remove(productId: number) {
    if (this.store.wishListSaving().includes(productId)) return;
    const items = this.store.wishListItems();
    const index = items.findIndex(item => item.productId === productId);
    const neighbour = items[index + 1] ?? items[index - 1];
    this.removing = productId;
    this.store.removeFromWishList(productId);
    if (neighbour) {
      afterNextRender(() => this.trashOf(neighbour.productId)?.focus(), { injector: this.injector });
    }
  }

  productCount(count: number): string {
    return count === 1 ? '1 producto' : `${count} productos`;
  }

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

  private trashOf(productId: number): HTMLElement | null {
    return this.host.querySelector<HTMLElement>(`[data-remove="${productId}"]`);
  }
}
