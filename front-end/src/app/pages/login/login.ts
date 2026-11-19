import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthStore } from '../../auth-store';
import { Toaster } from '../../core/services/toaster.service';
import { SignInRequest } from '../../core/models/user.model';
import { applyServerErrors, errorMessage, safeReturnUrl } from '../../core/util/error-message';
import { SfAlert, SfButton, SfField, SfInput } from '../../ui';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, SfAlert, SfButton, SfField, SfInput],
  templateUrl: './login.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Login {
  private fb = inject(FormBuilder);
  readonly authStore = inject(AuthStore);
  private router = inject(Router);
  private toaster = inject(Toaster);

  /** `?returnUrl=/checkout`: where to go after signing in (bound by withComponentInputBinding). */
  readonly returnUrl = input<string>();
  /** `?expired=true`: sent here because the session ran out (HU-08). */
  readonly expired = input<string>();

  /** Wrong credentials, too many attempts (429, with the wait) or no connection. */
  protected readonly formError = signal<string | null>(null);

  form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  submit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.formError.set(null);
    const credentials: SignInRequest = this.form.getRawValue();

    this.authStore.signIn(credentials).subscribe({
      next: () => {
        this.toaster.success('¡Hola de nuevo!');
        this.router.navigateByUrl(safeReturnUrl(this.returnUrl()) ?? '/products/all');
      },
      error: (err) => {
        if (!applyServerErrors(this.form, err)) this.formError.set(errorMessage(err));
      },
    });
  }
}
