import { ChangeDetectionStrategy, Component, Directive, input, output } from '@angular/core';
import { CircleAlert, LucideAngularModule, LucideIconData, RotateCw } from 'lucide-angular';
import { SfButton } from './button';

/** Empty view: what is missing, why, and one action (projected) to get out of it. */
@Component({
  selector: 'sf-empty-state',
  imports: [LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'sf-empty' },
  template: `
    <div class="sf-empty__icon"><lucide-icon [img]="icon()" /></div>
    <h2>{{ title() }}</h2>
    <p>{{ text() }}</p>
    <ng-content />
  `,
})
export class SfEmptyState {
  readonly icon = input.required<LucideIconData>();
  readonly title = input.required<string>();
  readonly text = input.required<string>();
}

/** Error in place of the content that failed, with a retry button. */
@Component({
  selector: 'sf-error-state',
  imports: [LucideAngularModule, SfButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'sf-error' },
  template: `
    <div class="sf-error__msg" role="alert">
      <lucide-icon [img]="alertIcon" />
      <span>{{ message() }}</span>
    </div>
    <button sfButton="secondary" type="button" (click)="retry.emit()">
      <lucide-icon [img]="retryIcon" />Reintentar
    </button>
  `,
})
export class SfErrorState {
  readonly message = input.required<string>();
  readonly retry = output<void>();

  protected readonly alertIcon = CircleAlert;
  protected readonly retryIcon = RotateCw;
}

/** Loading block in the shape of the content that is coming. Size it with utilities. */
@Directive({
  selector: '[sfSkeleton]',
  host: { class: 'sf-skel', 'aria-hidden': 'true' },
})
export class SfSkeleton {}
