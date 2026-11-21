import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { ResetPassword } from './reset-password';
import { AuthStore } from '../../auth-store';
import { aToken, testProviders } from '../../testing/test-providers';
import { environment } from '../../../environments/environment';

const api = environment.apiUrl;

describe('ResetPassword (HU-09)', () => {
  function render(token?: string) {
    localStorage.clear();
    TestBed.configureTestingModule({ imports: [ResetPassword], providers: testProviders() });
    const fixture = TestBed.createComponent(ResetPassword);
    if (token) fixture.componentRef.setInput('token', token);
    fixture.detectChanges();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const el = fixture.nativeElement as HTMLElement;
    const choose = (password: string) => {
      const input = el.querySelector<HTMLInputElement>('input[formControlName="password"]')!;
      input.value = password;
      input.dispatchEvent(new Event('input'));
      el.querySelector('form')!.dispatchEvent(new Event('submit'));
      fixture.detectChanges();
    };
    const text = () => {
      fixture.detectChanges();
      return el.textContent ?? '';
    };
    return { el, backend: TestBed.inject(HttpTestingController), navigate, choose, text };
  }

  it('sets the new password and signs in on this browser', () => {
    const { el, backend, navigate, choose } = render('abc');
    choose('nuevaClave99');

    expect(el.querySelector('button[type="submit"]')!.getAttribute('aria-busy')).toBe('true');
    const request = backend.expectOne(`${api}/auth/reset`);
    expect(request.request.body).toEqual({ token: 'abc', password: 'nuevaClave99' });
    request.flush({ token: aToken(), email: 'ana@mail.com', role: 'USER', emailVerified: true, deviceToken: 'device-1' });

    expect(TestBed.inject(AuthStore).isAuthenticated()).toBe(true);
    expect(localStorage.getItem('device_token')).toBe('device-1');
    expect(navigate).toHaveBeenCalledWith('/products/all');
  });

  it('says to ask for a new link when it expired or was used', () => {
    const { el, backend, choose, text } = render('viejo');
    choose('nuevaClave99');

    backend.expectOne(`${api}/auth/reset`).flush(
      { status: 400, detail: 'El enlace venció, pedí uno nuevo' },
      { status: 400, statusText: 'Bad Request' },
    );

    expect(text()).toContain('El enlace venció, pedí uno nuevo');
    expect(el.querySelector('a[href="/forgot-password"]')?.textContent).toContain('Pedir otro enlace');
    expect(el.querySelector('form')).toBeNull();
    expect(document.activeElement).toBe(el.querySelector('a[href="/forgot-password"]'));
  });

  it('keeps the form when the server cannot be reached, to try the same link again', () => {
    const { el, backend, choose, text } = render('abc');
    choose('nuevaClave99');

    backend.expectOne(`${api}/auth/reset`).error(new ProgressEvent('error'), { status: 0 });

    expect(text()).toContain('No pudimos conectar con el servidor');
    expect(el.querySelector('form')).not.toBeNull();
  });

  it('checks the length before calling the API', () => {
    const { backend, choose, text } = render('abc');
    choose('corta');

    backend.expectNone(`${api}/auth/reset`);
    expect(text()).toContain('Tiene que tener al menos 8 caracteres.');
  });

  it('explains an incomplete link without calling the API', () => {
    const { backend, text } = render();

    backend.expectNone(`${api}/auth/reset`);
    expect(text()).toContain('El enlace está incompleto');
  });
});
