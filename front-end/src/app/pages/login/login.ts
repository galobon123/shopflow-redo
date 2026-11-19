import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButton } from '@angular/material/button';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { AuthStore } from '../../auth-store';
import { Toaster } from '../../core/services/toaster.service';
import { SignInRequest } from '../../core/models/user.model';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButton, MatProgressSpinner, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  private fb = inject(FormBuilder);
  readonly authStore = inject(AuthStore);
  private router = inject(Router);
  private toaster = inject(Toaster);

  form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  submit() {
    if (this.form.invalid) return;
    const credentials: SignInRequest = this.form.getRawValue() as SignInRequest;
    this.authStore.signIn(credentials).subscribe({
      next: () => {
        this.toaster.success('¡Hola de nuevo!');
        this.router.navigate(['/products/all']);
      },
      error: (err) => this.toaster.error(err),
    });
  }
}