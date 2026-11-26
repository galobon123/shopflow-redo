import { ChangeDetectionStrategy, Component, ElementRef, afterRenderEffect, inject, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { LucideAngularModule, MailCheck } from 'lucide-angular';
import { AuthStore } from '../../auth-store';
import { applyServerErrors, errorMessage } from '../../core/util/error-message';
import { SfAlert, SfButton, SfEmptyState, SfField, SfInput } from '../../ui';

/**
 * HU-09 · "Olvidé mi contraseña": asks for the link by email. The answer is the same whether the
 * email has an account or not, so the page never tells who is registered.
 */
@Component({
  selector: 'app-forgot-password',
  imports: [ReactiveFormsModule, RouterLink, LucideAngularModule, SfAlert, SfButton, SfEmptyState, SfField, SfInput],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="min-h-[calc(100vh-var(--header-height))] flex items-center justify-center px-4 py-12 bg-canvas">
      <div class="w-full max-w-md bg-surface rounded border border-line p-8" aria-live="polite">
        @if (sent()) {
          <sf-empty-state [icon]="sentIcon" title="Revisá tu email"
            text="Si el email está registrado, te enviamos un enlace. Vence en 30 minutos.">
            <a #stateAction sfButton="primary" routerLink="/login">Volver a ingresar</a>
          </sf-empty-state>
        } @else {
          <h1 class="font-display text-heading">Recuperar contraseña</h1>
          <p class="text-body text-ink-muted mt-2 mb-8">
            Escribí el email de tu cuenta y te mandamos un enlace para elegir una contraseña nueva.
          </p>

          <form [formGroup]="form" (ngSubmit)="submit()" class="flex flex-col gap-5" novalidate>
            @if (formError(); as message) {
              <sf-alert>{{ message }}</sf-alert>
            }

            <sf-field class="max-w-none" label="Email"
              [errors]="{ required: 'Ingresá tu email.', email: 'Revisá el email: falta algo como nombre@mail.com.' }">
              <input sfInput type="email" formControlName="email" autocomplete="username" inputmode="email" />
            </sf-field>

            <button sfButton="primary" size="lg" block type="submit" [loading]="authStore.loading()">
              Mandame el enlace
            </button>
          </form>

          <p class="text-body-s text-ink-muted mt-6 text-center">
            ¿Te acordaste?
            <a class="sf-link" routerLink="/login">Ingresar</a>
          </p>
        }
      </div>
    </div>
  `,
})
export class ForgotPassword {
  private fb = inject(FormBuilder);
  protected readonly authStore = inject(AuthStore);

  /** No connection or the server failed; an unknown email is not an error. */
  protected readonly formError = signal<string | null>(null);
  protected readonly sent = signal(false);
  protected readonly sentIcon = MailCheck;
  private readonly stateAction = viewChild('stateAction', { read: ElementRef });

  constructor() {
    // The form (and the focused button) is gone: keep keyboard users on the new state, not on <body>.
    afterRenderEffect(() => this.stateAction()?.nativeElement.focus());
  }

  form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  submit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.formError.set(null);
    this.authStore.forgotPassword(this.form.getRawValue().email).subscribe({
      next: () => this.sent.set(true),
      error: (err) => {
        if (!applyServerErrors(this.form, err)) this.formError.set(errorMessage(err));
      },
    });
  }
}
