import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CircleCheck, LucideAngularModule, MailX } from 'lucide-angular';
import { AuthStore } from '../../auth-store';
import { errorMessage } from '../../core/util/error-message';
import { SfButton, SfEmptyState } from '../../ui';

type Status = 'checking' | 'verified' | 'failed';

/**
 * HU-31 · Where the link in the email lands (/verify-email?token=…). Confirms the email and
 * signs in on this browser; a used or expired link explains how to get another.
 */
@Component({
  selector: 'app-verify-email',
  imports: [RouterLink, LucideAngularModule, SfButton, SfEmptyState],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="min-h-[calc(100vh-var(--header-height))] flex items-center justify-center px-4 py-12 bg-canvas">
      <div class="w-full max-w-md bg-surface rounded border border-line p-8" aria-live="polite">
        @switch (status()) {
          @case ('checking') {
            <p class="text-body text-ink-muted text-center">Confirmando tu email…</p>
          }
          @case ('verified') {
            <sf-empty-state [icon]="okIcon" title="Tu email está confirmado"
              text="Ya podés comprar. Este dispositivo queda recordado para la próxima vez.">
              <a sfButton="primary" routerLink="/products/all">Ir a la tienda</a>
            </sf-empty-state>
          }
          @case ('failed') {
            <sf-empty-state [icon]="failedIcon" title="No pudimos confirmar tu email" [text]="error()">
              @if (authStore.isAuthenticated()) {
                <p class="text-body-s text-ink-muted">Pedí otro enlace desde el aviso de arriba.</p>
              } @else {
                <a sfButton="primary" routerLink="/login">Ingresar para pedir otro</a>
              }
            </sf-empty-state>
          }
        }
      </div>
    </div>
  `,
})
export class VerifyEmail implements OnInit {
  protected readonly authStore = inject(AuthStore);

  /** `?token=…` from the link (bound by withComponentInputBinding). */
  readonly token = input<string>();

  protected readonly status = signal<Status>('checking');
  protected readonly error = signal('');
  protected readonly okIcon = CircleCheck;
  protected readonly failedIcon = MailX;

  ngOnInit() {
    const token = this.token();
    if (!token) {
      this.fail('El enlace está incompleto. Copialo entero desde el email.');
      return;
    }
    this.authStore.verifyEmail(token).subscribe({
      next: () => this.status.set('verified'),
      error: (err) => this.fail(errorMessage(err)),
    });
  }

  private fail(message: string) {
    this.error.set(message);
    this.status.set('failed');
  }
}
