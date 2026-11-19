import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButton } from '@angular/material/button';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { AuthStore } from '../../auth-store';
import { Toaster } from '../../core/services/toaster.service';
import { SignUpRequest } from '../../core/models/auth.model';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButton, MatProgressSpinner, RouterLink],
  templateUrl: './register.html',
  styleUrl: './register.css',
})
export class Register {
  private fb = inject(FormBuilder);
  readonly authStore = inject(AuthStore);
  private router = inject(Router);
  private toaster = inject(Toaster);

  form = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(2)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  submit() {
    if (this.form.invalid) return;

    const data: SignUpRequest = this.form.getRawValue() as SignUpRequest;

    this.authStore.signUp(data).subscribe({
      next: () => {
        this.toaster.success('Tu cuenta está lista.');
        this.router.navigate(['/products/all']);
      },
      error: (err) => this.toaster.error(err),
    });
  }
}
