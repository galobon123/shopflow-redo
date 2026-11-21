import { ChangeDetectionStrategy, Component, ElementRef, OnInit, afterRenderEffect, inject, input, signal, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { LucideAngularModule, MailX } from 'lucide-angular';
import { AuthStore } from '../../auth-store';
import { Toaster } from '../../core/services/toaster.service';
import { applyServerErrors, errorMessage } from '../../core/util/error-message';
import { SfAlert, SfButton, SfEmptyState, SfField, SfInput } from '../../ui';

/**
 * HU-09 · "Nueva contraseña": where the link in the email lands (/reset-password?token=…).
 * Sets the new password and signs in on this browser; an expired or used link says how to get another.
 */
@Component({
  selector: 'app-reset-password',
  imports: [ReactiveFormsModule, RouterLink, LucideAngularModule, SfAlert, SfButton, SfEmptyState, SfField, SfInput],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="min-h-[calc(100vh-var(--header-height))] flex items-center justify-center px-4 py-12 bg-canvas">
      <div class="w-full max-w-md bg-surface rounded border border-line p-8" aria-live="polite">
        @if (linkError(); as message) {
          <sf-empty-state [icon]="failedIcon" title="No pudimos usar el enlace" [text]="message">
            <a #stateAction sfButton="primary" routerLink="/forgot-password">Pedir otro enlace</a>
          </sf-empty-state>
        } @else {
          <h1 class="font-display text-heading">Nueva contraseña</h1>
          <p class="text-body text-ink-muted mt-2 mb-8">Elegí la contraseña con la que vas a ingresar de ahora en más.</p>

          <form [formGroup]="form" (ngSubmit)="submit()" class="flex flex-col gap-5" novalidate>
            @if (formError(); as message) {
              <sf-alert>{{ message }}</sf-alert>
            }

            <sf-field class="max-w-none" label="Contraseña nueva" hint="Al menos 8 caracteres."
              [errors]="{ required: 'Elegí una contraseña.', minlength: 'Tiene que tener al menos 8 caracteres.', maxlength: 'Máximo 72 caracteres.' }">
              <input sfInput type="password" formControlName="password" autocomplete="new-password" />
            </sf-field>

            <button sfButton="primary" size="lg" block type="submit" [loading]="authStore.loading()">
              Guardar contraseña
            </button>
          </form>
        }
      </div>
    </div>
  `,
})
export class ResetPassword implements OnInit {
  private fb = inject(FormBuilder);
  protected readonly authStore = inject(AuthStore);
  private router = inject(Router);
  private toaster = inject(Toaster);

  /** `?token=…` from the link (bound by withComponentInputBinding). */
  readonly token = input<string>();

  /** The link can't be used (incomplete, expired or used): only asking for another one helps. */
  protected readonly linkError = signal<string | null>(null);
  /** No connection or the server failed: the same link can be tried again. */
  protected readonly formError = signal<string | null>(null);
  protected readonly failedIcon = MailX;
  private readonly stateAction = viewChild('stateAction', { read: ElementRef });

  constructor() {
    // The form (and the focused button) is gone: keep keyboard users on the new state, not on <body>.
    afterRenderEffect(() => this.stateAction()?.nativeElement.focus());
  }

  form = this.fb.nonNullable.group({
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
  });

  ngOnInit() {
    if (!this.token()) this.linkError.set('El enlace está incompleto. Copialo entero desde el email.');
  }

  submit() {
    const token = this.token();
    if (!token) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.formError.set(null);
    this.authStore.resetPassword(token, this.form.getRawValue().password).subscribe({
      next: () => {
        this.toaster.success('Listo, ya podés usar tu contraseña nueva.');
        this.router.navigateByUrl('/products/all');
      },
      error: (err) => {
        if (applyServerErrors(this.form, err)) return;
        // 400 without field errors: the link expired or was used.
        if (err instanceof HttpErrorResponse && err.status === 400) this.linkError.set(errorMessage(err));
        else this.formError.set(errorMessage(err));
      },
    });
  }
}
