import { HttpErrorResponse } from '@angular/common/http';
import { FormGroup } from '@angular/forms';

/** Error body the backend sends (RFC 9457 ProblemDetail, HU-03). */
export interface Problem {
  status: number;
  title?: string;
  detail?: string;
  errors?: Record<string, string>;
  errorId?: string;
  /** Why, when the browser has to react to it: 'session-replaced', 'order-in-progress' (HU-17). */
  reason?: string;
}

export const OFFLINE_MESSAGE = 'No pudimos conectar con el servidor. Revisá tu conexión y probá de nuevo.';
const FALLBACK_MESSAGE = 'Algo salió mal. Probá de nuevo.';

/**
 * Puts the backend's per-field errors (`errors.password = '…'`) on the matching form controls,
 * so each one shows under its field. Returns false when the response had none for this form.
 */
export function applyServerErrors(form: FormGroup, err: unknown): boolean {
  const errors = problemOf(err)?.errors ?? {};
  let applied = false;
  for (const [field, message] of Object.entries(errors)) {
    const control = form.get(field);
    if (!control) continue;
    control.setErrors({ server: message });
    control.markAsTouched();
    applied = true;
  }
  return applied;
}

/** Only paths inside the app (`/cart`, not `//evil.com` or `https://…`), so a link can't send people elsewhere. */
export function safeReturnUrl(url: string | null | undefined): string | null {
  return url && url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/\\') ? url : null;
}

export function problemOf(err: unknown): Problem | null {
  if (!(err instanceof HttpErrorResponse) || typeof err.error !== 'object' || err.error === null) return null;
  return err.error as Problem;
}

/**
 * The message a person should read for a failed request:
 * no connection → OFFLINE_MESSAGE, 4xx → the backend's `detail`, 5xx → generic text with the error code.
 */
export function errorMessage(err: unknown): string {
  if (!(err instanceof HttpErrorResponse)) return FALLBACK_MESSAGE;
  if (err.status === 0) return OFFLINE_MESSAGE;

  const problem = problemOf(err);
  if (err.status >= 500) {
    // The first block of the UUID is enough to find the full id in the server log.
    const code = problem?.errorId?.split('-')[0];
    return code ? `Algo falló de nuestro lado (código ${code}).` : 'Algo falló de nuestro lado.';
  }
  return problem?.detail ?? FALLBACK_MESSAGE;
}
