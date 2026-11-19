import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input, numberAttribute } from '@angular/core';

export type SfButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
export type SfIconButtonVariant = 'plain' | 'outlined' | 'float' | 'danger';

/** Text button. One `primary` per view; the accent colour is never a button fill. */
@Component({
  selector: 'button[sfButton], a[sfButton]',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'sf-btn',
    '[class]': 'variantClass()',
    '[class.sf-btn--sm]': "size() === 'sm'",
    '[class.sf-btn--lg]': "size() === 'lg'",
    '[class.sf-btn--block]': 'block()',
    '[attr.aria-busy]': 'loading() || null',
  },
  template: `
    @if (loading()) {
      <span class="sf-spinner" aria-hidden="true"></span>
    }
    <ng-content />
  `,
})
export class SfButton {
  /** `<button sfButton>` without a value is a primary button. */
  readonly sfButton = input<SfButtonVariant | ''>('primary');
  readonly size = input<'sm' | 'md' | 'lg'>('md');
  readonly block = input(false, { transform: booleanAttribute });
  readonly loading = input(false, { transform: booleanAttribute });

  protected readonly variantClass = computed(() => `sf-btn--${this.sfButton() || 'primary'}`);
}

/** Square 40px button with a single Lucide icon. Always give it an aria-label. */
@Component({
  selector: 'button[sfIconButton], a[sfIconButton]',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'sf-icon-btn',
    '[class.sf-icon-btn--outlined]': "variant() === 'outlined'",
    '[class.sf-icon-btn--float]': "variant() === 'float'",
    '[class.sf-icon-btn--danger]': "variant() === 'danger'",
    '[attr.aria-pressed]': 'pressed() ?? null',
  },
  template: `
    <ng-content />
    @if (count() > 0) {
      <span class="sf-count" aria-hidden="true">{{ count() > 99 ? '99+' : count() }}</span>
    }
  `,
})
export class SfIconButton {
  readonly sfIconButton = input<SfIconButtonVariant | ''>('plain');
  /** Toggle state (favourite marked). Leave undefined for plain actions. */
  readonly pressed = input<boolean | undefined>(undefined);
  readonly count = input(0, { transform: numberAttribute });

  protected readonly variant = computed(() => this.sfIconButton() || 'plain');
}
