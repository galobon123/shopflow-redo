import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AuthStore } from '../../auth-store';
import { Toaster } from '../../core/services/toaster.service';
import { SfAlert, SfButton } from '../../ui';

/**
 * HU-31 · Under the header while the signed-in person hasn't confirmed their email:
 * they can browse and fill the cart, but buying needs the link.
 */
@Component({
  selector: 'app-verify-email-banner',
  imports: [SfAlert, SfButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (authStore.isAuthenticated() && !authStore.emailVerified()) {
      <div class="mx-auto max-w-page px-4 pt-4">
        <sf-alert tone="info">
          <span class="flex flex-wrap items-center gap-x-4 gap-y-2">
            <span>Confirmá tu email para poder comprar: te mandamos un enlace a {{ authStore.userEmail() }}.</span>
            <button sfButton="ghost" size="sm" type="button" [loading]="sending()" (click)="resend()">
              Reenviar email
            </button>
          </span>
        </sf-alert>
      </div>
    }
  `,
})
export class VerifyEmailBanner {
  protected readonly authStore = inject(AuthStore);
  private readonly toaster = inject(Toaster);
  protected readonly sending = signal(false);

  resend() {
    this.sending.set(true);
    this.authStore.resendVerification().subscribe({
      next: () => {
        this.sending.set(false);
        // Confirmed on another device in the meantime: the refreshed session already hides the banner.
        if (this.authStore.emailVerified()) this.toaster.success('Tu email ya estaba confirmado.');
        else this.toaster.success('Te mandamos otro enlace. Revisá también la carpeta de spam.');
      },
      error: (err) => {
        this.sending.set(false);
        this.toaster.error(err);
      },
    });
  }
}
