import { inject, Injectable } from '@angular/core';
import { HotToastService } from '@ngxpert/hot-toast';
import { errorMessage } from '../util/error-message';

@Injectable({
  providedIn: 'root',
})
export class Toaster {
  toaster = inject(HotToastService);

  success(message: string) {
    this.toaster.success(message)
  }

  /** Pass a text, or the error from a failed request to show what the backend said (HU-03). */
  error(messageOrError: unknown) {
    this.toaster.error(typeof messageOrError === 'string' ? messageOrError : errorMessage(messageOrError))
  }

  info(message: string) {
    this.toaster.info(message)
  }
}
