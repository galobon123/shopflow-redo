import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthStore } from '../../auth-store';
import { Toaster } from '../../core/services/toaster.service';
import { SignInRequest } from '../../core/models/user.model';
import { LoginChallenge } from '../../core/models/auth.model';
import { applyServerErrors, errorMessage, safeReturnUrl } from '../../core/util/error-message';
import { GoogleSignIn } from '../../components/google-sign-in/google-sign-in';
import { SfAlert, SfButton, SfField, SfInput } from '../../ui';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, GoogleSignIn, SfAlert, SfButton, SfField, SfInput],
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
  /** HU-33 · Set when the password was right but this browser must confirm a code sent by email. */
  protected readonly challenge = signal<LoginChallenge | null>(null);
  protected readonly codeResent = signal(false);

  form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  codeForm = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  submit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.formError.set(null);
    const credentials: SignInRequest = this.form.getRawValue();

    this.authStore.signIn(credentials).subscribe({
      next: (challenge) => (challenge ? this.askForCode(challenge) : this.done()),
      error: (err) => {
        if (!applyServerErrors(this.form, err)) this.formError.set(errorMessage(err));
      },
    });
  }

  submitCode() {
    const challenge = this.challenge();
    if (!challenge) return;
    if (this.codeForm.invalid) {
      this.codeForm.markAllAsTouched();
      return;
    }
    this.formError.set(null);
    this.authStore.confirmCode(challenge.challengeId, this.codeForm.getRawValue().code).subscribe({
      next: () => this.done(),
      error: (err) => this.formError.set(errorMessage(err)),
    });
  }

  resendCode() {
    const challenge = this.challenge();
    if (!challenge) return;
    this.formError.set(null);
    this.authStore.resendCode(challenge.challengeId).subscribe({
      next: () => this.codeResent.set(true),
      error: (err) => this.formError.set(errorMessage(err)),
    });
  }

  /** Back to email and password, e.g. to use another account. */
  backToPassword() {
    this.challenge.set(null);
    this.formError.set(null);
    this.codeForm.reset();
  }

  done() {
    this.toaster.success('¡Hola de nuevo!');
    this.router.navigateByUrl(safeReturnUrl(this.returnUrl()) ?? '/products/all');
  }

  private askForCode(challenge: LoginChallenge) {
    this.challenge.set(challenge);
    this.codeResent.set(false);
    this.codeForm.reset();
  }
}
