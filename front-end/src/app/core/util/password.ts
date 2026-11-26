import { AbstractControl, ValidationErrors } from '@angular/forms';

/**
 * HU-06 + HU-09 · Same limit as the backend: BCrypt only uses 72 UTF-8 bytes, so "ñ" or "á" count twice.
 * Error key `tooLong`, so the message doesn't promise a number of characters.
 */
export function passwordFits(control: AbstractControl<string>): ValidationErrors | null {
  return new TextEncoder().encode(control.value ?? '').length > 72 ? { tooLong: true } : null;
}
