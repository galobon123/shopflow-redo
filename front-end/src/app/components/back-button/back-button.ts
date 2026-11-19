import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ArrowLeft, LucideAngularModule } from 'lucide-angular';
import { SfButton } from '../../ui';

@Component({
  selector: 'app-back-button',
  imports: [SfButton, RouterLink, LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './back-button.html',
  styleUrl: './back-button.css',
})
export class BackButton {
  navigateTo = input<string>();

  protected readonly backIcon = ArrowLeft;
}
