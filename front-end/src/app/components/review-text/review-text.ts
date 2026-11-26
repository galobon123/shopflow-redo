import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, afterNextRender, inject, input, signal, viewChild } from '@angular/core';

let nextId = 0;

/**
 * HU-30 · A review's comment, cut at 5 lines with "Ver más" when it's longer, so a 1000-character review
 * doesn't push the rest of the page down. Measured on screen: whether it fits depends on the width.
 */
@Component({
  selector: 'app-review-text',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p #paragraph class="sf-review__text" [id]="id" [class.sf-review__text--clamped]="!expanded()"
      [class.sf-review__text--faded]="overflows() && !expanded()">{{ text() }}</p>
    @if (overflows() || expanded()) {
      <button class="sf-review__more" type="button" [attr.aria-expanded]="expanded()" [attr.aria-controls]="id"
        (click)="expanded.set(!expanded())">
        {{ expanded() ? 'Ver menos' : 'Ver más' }}
      </button>
    }
  `,
  host: { class: 'grid justify-items-start gap-1' },
})
export class ReviewText {
  readonly text = input.required<string>();

  protected readonly id = `review-text-${nextId++}`;
  protected readonly expanded = signal(false);
  /** Taller than the 5 lines it's cut at; only measured while cut. */
  protected readonly overflows = signal(false);

  private readonly paragraph = viewChild.required<ElementRef<HTMLElement>>('paragraph');

  constructor() {
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      const el = this.paragraph().nativeElement;
      const measure = () => {
        if (!this.expanded()) this.overflows.set(el.scrollHeight > el.clientHeight + 1);
      };
      const resizes = new ResizeObserver(measure);
      resizes.observe(el);
      destroyRef.onDestroy(() => resizes.disconnect());
    });
  }
}
