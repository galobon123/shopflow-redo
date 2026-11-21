import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { VerifyEmail } from './verify-email';
import { AuthStore } from '../../auth-store';
import { aToken, testProviders } from '../../testing/test-providers';
import { environment } from '../../../environments/environment';

const api = environment.apiUrl;

describe('VerifyEmail (HU-31)', () => {
  function render(token?: string) {
    localStorage.clear();
    TestBed.configureTestingModule({ imports: [VerifyEmail], providers: testProviders() });
    const fixture = TestBed.createComponent(VerifyEmail);
    if (token) fixture.componentRef.setInput('token', token);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const text = () => {
      fixture.detectChanges();
      return el.textContent ?? '';
    };
    return { backend: TestBed.inject(HttpTestingController), text };
  }

  it('confirms the email with the link token and signs in on this browser', () => {
    const { backend, text } = render('abc');

    const request = backend.expectOne(`${api}/auth/verify-email`);
    expect(request.request.body).toEqual({ token: 'abc' });
    request.flush({ token: aToken(), email: 'ana@mail.com', role: 'USER', emailVerified: true, deviceToken: 'device-1' });

    expect(text()).toContain('Tu email está confirmado');
    expect(TestBed.inject(AuthStore).isAuthenticated()).toBe(true);
    expect(localStorage.getItem('device_token')).toBe('device-1');
  });

  it('explains a used or expired link', () => {
    const { backend, text } = render('viejo');

    backend.expectOne(`${api}/auth/verify-email`).flush(
      { status: 400, detail: 'El enlace venció o ya se usó. Pedí uno nuevo desde tu cuenta.' },
      { status: 400, statusText: 'Bad Request' },
    );

    expect(text()).toContain('El enlace venció o ya se usó');
    expect(text()).toContain('Ingresar para pedir otro');
  });

  it('does not call the API without a token', () => {
    const { backend, text } = render();

    backend.expectNone(`${api}/auth/verify-email`);
    expect(text()).toContain('El enlace está incompleto');
  });
});
