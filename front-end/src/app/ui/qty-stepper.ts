import { ChangeDetectionStrategy, Component, computed, inject, input, numberAttribute, output, signal } from '@angular/core';
import { LucideAngularModule, Minus, Plus } from 'lucide-angular';
import { Toaster } from '../core/services/toaster.service';

/**
 * Quantity selector for cart and product detail. Asking for more than `max` shakes the number and a toast says
 * how many are left.
 * The number can be typed (kept on Enter or on leaving the field, pulled back between min and max) and the
 * up and down arrows change it by one. At either end the buttons are aria-disabled, not disabled, so keyboard
 * focus never drops to the page.
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
      <input #field type="text" inputmode="numeric" autocomplete="off" aria-label="Cantidad" [value]="value()"
        [class.sf-stepper__shake]="shaking()" (animationend)="shaking.set(false)" (change)="commit(field)" (keydown.enter)="commit(field)"
        (keydown.arrowup)="$event.preventDefault(); increment()" (keydown.arrowdown)="$event.preventDefault(); decrement()" />
      <button type="button" aria-label="Sumar uno" [attr.aria-disabled]="atMax() ? 'true' : null" (click)="increment()">
        <lucide-icon [img]="plus" [size]="16" />
      </button>
    </div>
    <!-- The +/- buttons keep the focus: this says the new number. -->
    <span class="sr-only" aria-live="polite">{{ value() }}</span>
  `,
})
export class SfQtyStepper {
  readonly value = input.required({ transform: numberAttribute });
  readonly min = input(1, { transform: numberAttribute });
  readonly max = input(Number.POSITIVE_INFINITY, { transform: numberAttribute });
  readonly valueChange = output<number>();

  private readonly toaster = inject(Toaster);
  protected readonly shaking = signal(false);
  protected readonly atMin = computed(() => this.value() <= this.min());
  protected readonly atMax = computed(() => this.value() >= this.max());
  protected readonly minus = Minus;
  protected readonly plus = Plus;

  protected decrement() {
    if (!this.atMin()) this.valueChange.emit(this.value() - 1);
  }

  protected increment() {
    if (this.atMax()) this.tooMany();
    else this.valueChange.emit(this.value() + 1);
  }

  /** A typed number, pulled back between min and max; anything else goes back to the current one. */
  protected commit(field: HTMLInputElement) {
    const typed = Number.parseInt(field.value.replace(/\D/g, ''), 10);
    const next = Number.isNaN(typed) ? this.value() : Math.min(Math.max(typed, this.min()), this.max());
    if (typed > this.max()) this.tooMany();
    // Set by hand: if it ends equal to the current value, the binding has nothing new to write.
    field.value = String(next);
    if (next !== this.value()) this.valueChange.emit(next);
  }

  private tooMany() {
    this.shaking.set(true);
    const left = this.max();
    this.toaster.info(left === 1 ? 'Solo queda 1 en stock.' : `Solo quedan ${left} en stock.`);
  }
}
