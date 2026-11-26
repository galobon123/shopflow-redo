import { inject, Injectable } from '@angular/core';
import { CreateHotToastRef, HotToastService } from '@ngxpert/hot-toast';
import { errorMessage } from '../util/error-message';

/**
 * Toasts, never repeated: while a toast is on screen, the same message is not shown again nor queued.
 * Buttons keep working as usual (pressing "Agregar" 5 times adds 5); only the toast is shown once,
 * and pressing again after it closes shows it again.
 */
@Injectable({
  providedIn: 'root',
})
export class Toaster {
  toaster = inject(HotToastService);

  private readonly onScreen = new Set<string>();

  success(message: string) {
    this.once(message, () => this.toaster.success(message));
  }

  /** Pass a text, or the error from a failed request to show what the backend said (HU-03). */
  error(messageOrError: unknown) {
    const message = typeof messageOrError === 'string' ? messageOrError : errorMessage(messageOrError);
    this.once(message, () => this.toaster.error(message));
  }

  info(message: string) {
    this.once(message, () => this.toaster.info(message));
  }

  private once(message: string, show: () => CreateHotToastRef<unknown>) {
    if (this.onScreen.has(message)) return;
    this.onScreen.add(message);
    show().afterClosed.subscribe(() => this.onScreen.delete(message));
  }
}
