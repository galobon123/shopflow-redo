import { ChangeDetectionStrategy, Component, computed, input, numberAttribute } from '@angular/core';
import { Check, LucideAngularModule } from 'lucide-angular';

export type SfBadgeTone = 'stock' | 'low' | 'out' | 'promo' | 'new';

/** Low stock threshold: at or below this, the badge says how many are left. */
export const LOW_STOCK = 5;

/** Short status or promo label. Always a word; colour only backs it up. */
@Component({
  selector: 'sf-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'sf-badge', '[class]': "'sf-badge--' + tone()" },
  template: `<ng-content />`,
})
export class SfBadge {
  readonly tone = input.required<SfBadgeTone>();
}

/** Stock badge computed from the units left: En stock / Quedan N / Sin stock. */
@Component({
  selector: 'sf-stock-badge',
  imports: [SfBadge, LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @switch (state()) {
      @case ('out') { <sf-badge tone="out">Sin stock</sf-badge> }
      @case ('low') { <sf-badge tone="low">Quedan {{ stock() }}</sf-badge> }
      @default { <sf-badge tone="stock"><lucide-icon [img]="check" [size]="16" />En stock</sf-badge> }
    }
  `,
})
export class SfStockBadge {
  readonly stock = input.required({ transform: numberAttribute });

  protected readonly state = computed(() => {
    const stock = this.stock();
    if (stock <= 0) return 'out';
    return stock <= LOW_STOCK ? 'low' : 'stock';
  });
  protected readonly check = Check;
}
