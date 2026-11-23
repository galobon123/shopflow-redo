import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { Location, NgTemplateOutlet } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { ArrowLeft, LucideAngularModule } from 'lucide-angular';
import { SfButton } from '../../ui';

/**
 * Back to a fixed page (`navigateTo`) or, without it, to the previous screen. Opened from a link or a
 * reload there is no previous screen in the shop, so it goes to the catalogue instead of leaving the site.
 */
@Component({
  selector: 'app-back-button',
  imports: [SfButton, RouterLink, LucideAngularModule, NgTemplateOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './back-button.html',
  styleUrl: './back-button.css',
})
export class BackButton {
  private readonly router = inject(Router);
  private readonly location = inject(Location);

  navigateTo = input<string>();

  protected readonly backIcon = ArrowLeft;

  protected goBack() {
    if (this.router.lastSuccessfulNavigation()?.previousNavigation) this.location.back();
    else this.router.navigateByUrl('/products/all');
  }
}
