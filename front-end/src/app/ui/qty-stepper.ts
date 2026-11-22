import { ChangeDetectionStrategy, Component, computed, input, numberAttribute, output } from '@angular/core';
import { LucideAngularModule, Minus, Plus } from 'lucide-angular';

/**
 * Quantity selector for cart and product detail. At `max` it says how many are left.
 * At either end the buttons are aria-disabled, not disabled, so keyboard focus never drops to the page.
 */
@Component({
  selector: 'sf-qty-stepper',
  imports: [LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'inline-flex items-center gap-3' },
  template: `
    <div class="sf-stepper" role="group" aria-label="Cantidad">
      <button type="button" aria-label="Restar uno" [attr.aria-disabled]="atMin() ? 'true' : null" (click)="decrement()">
        <lucide-icon [img]="minus" [size]="16" />
      </button>
      <output aria-live="polite">{{ value() }}</output>
      <button type="button" aria-label="Sumar uno" [attr.aria-disabled]="atMax() ? 'true' : null" (click)="increment()">
        <lucide-icon [img]="plus" [size]="16" />
      </button>
    </div>
    @if (atMax()) {
      <span class="text-body-s text-ink-muted">Quedan {{ max() }} en stock</span>
    }
  `,
})
export class SfQtyStepper {
  readonly value = input.required({ transform: numberAttribute });
  readonly min = input(1, { transform: numberAttribute });
  readonly max = input(Number.POSITIVE_INFINITY, { transform: numberAttribute });
  readonly valueChange = output<number>();

  protected readonly atMin = computed(() => this.value() <= this.min());
  protected readonly atMax = computed(() => this.value() >= this.max());
  protected readonly minus = Minus;
  protected readonly plus = Plus;

  protected decrement() {
    if (!this.atMin()) this.valueChange.emit(this.value() - 1);
  }

  protected increment() {
    if (!this.atMax()) this.valueChange.emit(this.value() + 1);
  }
}
