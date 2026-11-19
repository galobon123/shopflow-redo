import { HttpErrorResponse } from '@angular/common/http';
import { errorMessage, OFFLINE_MESSAGE } from './error-message';

const httpError = (status: number, error: unknown = null) => new HttpErrorResponse({ status, error });

describe('errorMessage (HU-03)', () => {
  it('says the server is unreachable when there is no response', () => {
    expect(errorMessage(httpError(0))).toBe(OFFLINE_MESSAGE);
  });

  it('never blames the credentials when the server is down', () => {
    expect(errorMessage(httpError(0))).not.toContain('contraseña');
  });

  it('shows the backend detail for 4xx', () => {
    const err = httpError(401, { status: 401, title: 'No autenticado', detail: 'Email o contraseña incorrectos.' });
    expect(errorMessage(err)).toBe('Email o contraseña incorrectos.');
  });

  it('shows a generic text with the error code for 5xx', () => {
    const err = httpError(500, { status: 500, errorId: '95a5e82c-f22d-4eea-8417-acaa239a2e6d' });
    expect(errorMessage(err)).toBe('Algo falló de nuestro lado (código 95a5e82c).');
  });

  it('falls back when the 4xx body is not a ProblemDetail', () => {
    expect(errorMessage(httpError(404, 'Not Found'))).toBe('Algo salió mal. Probá de nuevo.');
  });

  it('falls back for non-HTTP errors', () => {
    expect(errorMessage(new Error('boom'))).toBe('Algo salió mal. Probá de nuevo.');
  });
});
