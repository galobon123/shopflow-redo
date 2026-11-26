import {
  ChangeDetectionStrategy, Component, ElementRef, computed, input, linkedSignal, signal, viewChild, viewChildren,
} from '@angular/core';
import { ChevronLeft, ChevronRight, LucideAngularModule, X } from 'lucide-angular';
import { SfIconButton } from '../../ui';

/** A horizontal swipe shorter than this is a tap, not a change of photo. */
const SWIPE_PX = 40;

/**
 * HU-11 · Product photos: main photo, arrows, thumbnails, keyboard arrows and swipe on phones. Changing photo
 * slides to the next one, and on phones the photo follows the finger.
 * Never moves on its own (kit rule, WCAG 2.2.2). Tapping the main photo opens it full screen at its
 * original resolution; Esc, the close button or a tap outside the photo close it and focus goes back.
 * At either end the arrows are aria-disabled, not disabled, so keyboard focus never drops to the page.
 */
@Component({
  selector: 'app-product-gallery',
  imports: [LucideAngularModule, SfIconButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-gallery.html',
  host: { class: 'sf-gallery', '(keydown)': 'onKeydown($event)' },
})
export class ProductGallery {
  readonly images = input.required<string[]>();
  /** Alt text, and what the well shows when there is no photo. */
  readonly name = input.required<string>();

  /** Back to the cover when the photos change (another product). */
  protected readonly index = linkedSignal({ source: this.images, computation: () => 0 });
  protected readonly current = computed(() => this.images()[this.index()]);
  /** Photos that failed to load: the well shows the name instead. */
  protected readonly failed = linkedSignal({ source: this.images, computation: () => new Set<number>() });
  protected readonly zoomed = signal(false);

  private readonly zoom = viewChild<ElementRef<HTMLDialogElement>>('zoom');
  private readonly zoomPhoto = viewChild<ElementRef<HTMLImageElement>>('zoomPhoto');
  private readonly slides = viewChildren<ElementRef<HTMLElement>>('slide');
  private readonly thumbs = viewChildren<ElementRef<HTMLButtonElement>>('thumb');
  private touchStart: { x: number; y: number } | null = null;
  /** How far the finger dragged the photo, in px; null when not dragging (then it slides with a transition). */
  protected readonly dragX = signal<number | null>(null);
  protected readonly offset = computed(() => `translateX(calc(${this.index() * -100}% + ${this.dragX() ?? 0}px))`);

  protected readonly icons = { prev: ChevronLeft, next: ChevronRight, close: X };

  show(index: number) {
    const last = this.images().length - 1;
    this.index.set(Math.min(Math.max(index, 0), last));
  }

  protected onKeydown(event: KeyboardEvent) {
    if (this.zoomed() || this.images().length < 2) return;
    const step = event.key === 'ArrowLeft' ? -1 : event.key === 'ArrowRight' ? 1 : 0;
    if (!step) return;
    event.preventDefault();
    this.show(this.index() + step);
    // On a thumbnail, focus follows the selected photo.
    const thumbs = this.thumbs();
    if (thumbs.some((t) => t.nativeElement === document.activeElement)) {
      thumbs[this.index()].nativeElement.focus();
    }
  }

  protected onTouchStart(event: TouchEvent) {
    const touch = event.changedTouches[0];
    this.touchStart = touch ? { x: touch.clientX, y: touch.clientY } : null;
  }

  /** The photo follows the finger sideways; past the first or last one it gives only a little, like a rubber band. */
  protected onTouchMove(event: TouchEvent) {
    const touch = event.changedTouches[0];
    if (!this.touchStart || !touch) return;
    const dx = touch.clientX - this.touchStart.x;
    if (Math.abs(touch.clientY - this.touchStart.y) > Math.abs(dx)) return; // scrolling the page
    const atEnd = (dx > 0 && this.index() === 0) || (dx < 0 && this.index() === this.images().length - 1);
    this.dragX.set(atEnd ? dx / 3 : dx);
  }

  protected onTouchEnd(event: TouchEvent) {
    const endX = event.changedTouches[0]?.clientX;
    this.dragX.set(null);
    if (this.touchStart === null || endX === undefined) return;
    const dx = endX - this.touchStart.x;
    this.touchStart = null;
    if (Math.abs(dx) >= SWIPE_PX) this.show(this.index() + (dx < 0 ? 1 : -1));
  }

  protected onImageError(index: number) {
    this.failed.update((failed) => new Set(failed).add(index));
  }

  protected openZoom() {
    if (!this.current() || this.failed().has(this.index())) return;
    this.zoom()?.nativeElement.showModal();
    this.zoomed.set(true);
    if (this.zoomPhoto()?.nativeElement.complete) this.centerZoom(); // cached: no load event
  }

  /** On a phone the original photo is bigger than the screen: open on its middle, not its top-left corner. */
  protected centerZoom() {
    const dialog = this.zoom()?.nativeElement;
    if (!dialog?.open) return;
    dialog.scrollLeft = (dialog.scrollWidth - dialog.clientWidth) / 2;
    dialog.scrollTop = (dialog.scrollHeight - dialog.clientHeight) / 2;
  }

  protected closeZoom() {
    this.zoom()?.nativeElement.close();
  }

  /** The dialog closed (button, outside tap or Esc): focus goes back to the photo that opened it. */
  protected onZoomClosed() {
    this.zoomed.set(false);
    this.slides()[this.index()]?.nativeElement.querySelector('button')?.focus();
  }

  /** A tap anywhere but on the photo closes the zoom. */
  protected onZoomClick(event: MouseEvent) {
    if (!(event.target instanceof HTMLImageElement)) this.closeZoom();
  }
}
