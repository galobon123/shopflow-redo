import { ChangeDetectionStrategy, Component, computed, input, numberAttribute } from '@angular/core';
import { LucideAngularModule, Star } from 'lucide-angular';

const ONE_DECIMAL = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 1 });

/** HU-30 · A rating the way people read it: `4,2`, `4`. */
export function formatRating(rating: number): string {
  return ONE_DECIMAL.format(rating);
}

/**
 * HU-30 · Read-only stars, filled up to the rating (4,2 fills 84% of the row).
 * Screen readers get the number instead of five icons: "4,2 de 5 estrellas".
 */
@Component({
  selector: 'sf-stars',
  imports: [LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'sf-stars', role: 'img', '[attr.aria-label]': 'label()' },
  template: `
    @for (i of five; track i) { <lucide-icon [img]="star" [size]="size()" /> }
    <span class="sf-stars__fill" [style.width.%]="percent()">
      @for (i of five; track i) { <lucide-icon [img]="star" [size]="size()" /> }
    </span>
  `,
})
export class SfStars {
  readonly rating = input.required({ transform: numberAttribute });
  readonly size = input(16, { transform: numberAttribute });

  protected readonly five = [1, 2, 3, 4, 5];
  protected readonly star = Star;
  protected readonly percent = computed(() => Math.min(Math.max(this.rating(), 0), 5) * 20);
  protected readonly label = computed(() => `${formatRating(this.rating())} de 5 estrellas`);
}
