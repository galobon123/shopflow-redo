import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthStore } from '../../auth-store';
import { Toaster } from '../../core/services/toaster.service';
import { SignUpRequest } from '../../core/models/auth.model';
import { applyServerErrors, safeReturnUrl } from '../../core/util/error-message';
import { SfButton, SfField, SfInput } from '../../ui';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, SfButton, SfField, SfInput],
  templateUrl: './register.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Register {
  private fb = inject(FormBuilder);
  readonly authStore = inject(AuthStore);
  private router = inject(Router);
  private toaster = inject(Toaster);

  /** `?returnUrl=/checkout`: where to go once the account exists (bound by withComponentInputBinding). */
  readonly returnUrl = input<string>();

  form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
  });

  submit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const data: SignUpRequest = this.form.getRawValue();

    this.authStore.signUp(data).subscribe({
      next: () => {
        this.toaster.success('Tu cuenta está lista.');
        this.router.navigateByUrl(safeReturnUrl(this.returnUrl()) ?? '/products/all');
      },
      error: (err) => {
        // 400 with errors per field → under each field; anything else (409, offline…) → toast.
        if (!applyServerErrors(this.form, err)) this.toaster.error(err);
      },
    });
  }
}
