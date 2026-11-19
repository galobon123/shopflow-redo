import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { CircleAlert, Info, LucideAngularModule } from 'lucide-angular';

/**
 * One message about a whole form or page: "Email o contraseña incorrectos.", "Tu sesión venció."
 * Errors of a single field go in its sf-field, not here. `danger` is announced right away (role=alert).
 */
@Component({
  selector: 'sf-alert',
  imports: [LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class]': '"sf-alert sf-alert--" + tone()',
    '[attr.role]': 'tone() === "danger" ? "alert" : "status"',
  },
  template: `<lucide-icon [img]="icon()" [size]="16" /><span><ng-content /></span>`,
})
export class SfAlert {
  readonly tone = input<'danger' | 'info'>('danger');
  protected readonly icon = computed(() => (this.tone() === 'danger' ? CircleAlert : Info));
}
