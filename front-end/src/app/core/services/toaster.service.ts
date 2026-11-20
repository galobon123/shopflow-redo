import { DOCUMENT, inject, Injectable } from '@angular/core';
import { CreateHotToastRef, HotToastService } from '@ngxpert/hot-toast';
import { errorMessage } from '../util/error-message';

/** What counts as "the button" of a click: the nearest clickable thing around the target. */
const CLICKABLE = 'button, a, [role="button"]';
/** A toast this long after a click is still that click's answer (it may wait for the backend). */
const CLICK_TO_TOAST_MS = 10_000;

/**
 * Toasts, plus one rule: the button whose click made a toast ignores further clicks until that toast
 * closes, so pressing "Agregar" again and again doesn't repeat the action or pile up toasts.
 * Other buttons keep working. Buttons with their own saving state (`data-no-toast-lock`, like the favourite
 * heart) are left alone: they answer again as soon as they show they're done.
 * Created at startup (app.config) so it sees the very first click.
 */
@Injectable({
  providedIn: 'root',
})
export class Toaster {
  toaster = inject(HotToastService);

  private lastClick: { element: Element; at: number } | null = null;
  private readonly locked = new Set<Element>();

  constructor() {
    const document = inject(DOCUMENT);
    // Capture phase: runs before the button's own handlers, so a locked click never reaches them.
    document.addEventListener('click', (event) => this.onClick(event), { capture: true });
    // Typing (e.g. Enter in a form) is not a click: its toast must not lock a button clicked earlier.
    document.addEventListener('keydown', () => (this.lastClick = null), { capture: true });
  }

  success(message: string) {
    this.lockUntilClosed(this.toaster.success(message));
  }

  /** Pass a text, or the error from a failed request to show what the backend said (HU-03). */
  error(messageOrError: unknown) {
    this.lockUntilClosed(
      this.toaster.error(typeof messageOrError === 'string' ? messageOrError : errorMessage(messageOrError)),
    );
  }

  info(message: string) {
    this.lockUntilClosed(this.toaster.info(message));
  }

  private onClick(event: Event) {
    const element = (event.target as Element | null)?.closest?.(CLICKABLE);
    if (!element || element.hasAttribute('data-no-toast-lock')) return;
    if (this.locked.has(element)) {
      event.preventDefault();
      event.stopImmediatePropagation();
      return;
    }
    this.lastClick = { element, at: Date.now() };
  }

  private lockUntilClosed(toast: CreateHotToastRef<unknown>) {
    const click = this.lastClick;
    this.lastClick = null;
    if (!click || Date.now() - click.at > CLICK_TO_TOAST_MS || !click.element.isConnected) return;
    const { element } = click;
    this.locked.add(element);
    toast.afterClosed.subscribe(() => this.locked.delete(element));
  }
}
