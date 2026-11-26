import { ChangeDetectionStrategy, Component, Directive, computed, input } from '@angular/core';
import { CircleAlert, Info, LucideAngularModule, TriangleAlert } from 'lucide-angular';

export type SfAlertTone = 'danger' | 'warning' | 'info';

const ICONS = { danger: CircleAlert, warning: TriangleAlert, info: Info };

/**
 * One message about a whole form or page: "Email o contraseña incorrectos.", "Tu sesión venció."
 * Errors of a single field go in its sf-field, not here. `danger` is announced right away (role=alert).
 * Every tone shares the layout: icon centered on the text, and an optional action ([sfAlertAction])
 * pushed to the end, which drops below the text when the line is too narrow.
 */
@Component({
  selector: 'sf-alert',
  imports: [LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class]': '"sf-alert sf-alert--" + tone()',
    '[attr.role]': 'tone() === "danger" ? "alert" : "status"',
  },
  template: `
    <lucide-icon [img]="icon()" [size]="16" />
    <span class="sf-alert__body">
      <span class="sf-alert__text"><ng-content /></span>
      <ng-content select="[sfAlertAction]" />
    </span>
  `,
})
export class SfAlert {
  readonly tone = input<SfAlertTone>('danger');
  protected readonly icon = computed(() => ICONS[this.tone()]);
}

/** Marks the button or link that goes at the end of an sf-alert. */
@Directive({ selector: '[sfAlertAction]', host: { class: 'sf-alert__action' } })
export class SfAlertAction {}
