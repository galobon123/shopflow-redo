import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CdkMenu, CdkMenuItem, CdkMenuTrigger } from '@angular/cdk/menu';
import { ConnectedPosition } from '@angular/cdk/overlay';
import { ChevronDown, Heart, LogOut, LucideAngularModule, ShieldCheck, ShoppingBag } from 'lucide-angular';
import { EcommerceStore } from '../../ecommerce-store';
import { AuthStore } from '../../auth-store';
import { Toaster } from '../../core/services/toaster.service';
import { SfButton, SfIconButton } from '../../ui';

@Component({
  selector: 'app-header-actions',
  imports: [RouterLink, CdkMenu, CdkMenuItem, CdkMenuTrigger, SfButton, SfIconButton, LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './header-actions.html',
  styleUrl: './header-actions.css',
})
export class HeaderActions {
  store = inject(EcommerceStore);
  readonly authStore = inject(AuthStore);
  private router = inject(Router);
  private toaster = inject(Toaster);

  protected readonly icons = {
    heart: Heart,
    bag: ShoppingBag,
    chevronDown: ChevronDown,
    shield: ShieldCheck,
    logout: LogOut,
  };

  /** Profile menu opens under the button, aligned to its right edge. */
  protected readonly menuPosition: ConnectedPosition[] = [
    { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top' },
  ];

  protected readonly firstName = computed(() => (this.authStore.userName() ?? '').split(' ')[0]);
  protected readonly initials = computed(() =>
    (this.authStore.userName() ?? this.authStore.userEmail() ?? '?')
      .split(' ')
      .slice(0, 2)
      .map(word => word[0]?.toUpperCase() ?? '')
      .join('')
  );

  logout(): void {
    this.authStore.logout();
    this.toaster.info('Cerraste sesión.');
    this.router.navigate(['/products/all']);
  }
}
