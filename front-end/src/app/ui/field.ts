import { Component, Directive, contentChild, inject, input } from '@angular/core';
import { NgControl } from '@angular/forms';
import { CircleAlert, LucideAngularModule } from 'lucide-angular';

let nextId = 0;

/**
 * Form field: visible label, a 44px control (projected, marked with `sfInput`) and help or error text below.
 * Errors show once the control was touched, using the message mapped to the first failing validator.
 *
 * Default change detection on purpose: touched/invalid live on the projected control, not on a signal.
 */
@Component({
  selector: 'sf-field',
  imports: [LucideAngularModule],
  host: { class: 'sf-field' },
  template: `
    <label class="sf-field__label" [for]="id">{{ label() }}</label>
    <ng-content />
    @if (error(); as message) {
      <span class="sf-field__error" [id]="messageId">
        <lucide-icon [img]="alertIcon" [size]="16" />{{ message }}
      </span>
    } @else if (hint()) {
      <span class="sf-field__help" [id]="messageId">{{ hint() }}</span>
    }
  `,
})
export class SfField {
  readonly label = input.required<string>();
  readonly hint = input<string>();
  /** Message per validator key, e.g. `{ required: 'Ingresá tu email.' }`. */
  readonly errors = input<Record<string, string>>({});

  readonly id = `sf-field-${nextId++}`;
  readonly messageId = `${this.id}-msg`;
  protected readonly alertIcon = CircleAlert;

  private readonly control = contentChild(NgControl);

  error(): string | null {
    const control = this.control();
    if (!control?.invalid || !control.touched || !control.errors) return null;
    const key = Object.keys(control.errors)[0];
    return this.errors()[key] ?? 'Revisá este campo.';
  }

  describedBy(): string | null {
    return this.error() || this.hint() ? this.messageId : null;
  }
}

/** Marks the control inside an `sf-field` and wires id, aria-invalid and aria-describedby. */
@Directive({
  selector: 'input[sfInput], select[sfInput], textarea[sfInput]',
  host: {
    class: 'sf-input',
    '[id]': 'field?.id',
    '[attr.aria-invalid]': 'field?.error() ? true : null',
    '[attr.aria-describedby]': 'field?.describedBy() ?? null',
  },
})
export class SfInput {
  protected readonly field = inject(SfField, { optional: true });
}
