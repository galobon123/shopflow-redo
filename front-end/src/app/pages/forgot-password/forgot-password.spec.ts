import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { ForgotPassword } from './forgot-password';
import { testProviders } from '../../testing/test-providers';
import { environment } from '../../../environments/environment';

const api = environment.apiUrl;

describe('ForgotPassword (HU-09)', () => {
  function render() {
    localStorage.clear();
    TestBed.configureTestingModule({ imports: [ForgotPassword], providers: testProviders() });
    const fixture = TestBed.createComponent(ForgotPassword);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const ask = (email: string) => {
      const input = el.querySelector<HTMLInputElement>('input[formControlName="email"]')!;
      input.value = email;
      input.dispatchEvent(new Event('input'));
      el.querySelector('form')!.dispatchEvent(new Event('submit'));
      fixture.detectChanges();
    };
    const text = () => {
      fixture.detectChanges();
      return el.textContent ?? '';
    };
    return { el, backend: TestBed.inject(HttpTestingController), ask, text };
  }

  it('asks for the link and says the same whether the email exists or not', () => {
    const { el, backend, ask, text } = render();
    ask('nadie@mail.com');

    expect(el.querySelector('button[type="submit"]')!.getAttribute('aria-busy')).toBe('true');
    const request = backend.expectOne(`${api}/auth/forgot`);
    expect(request.request.body).toEqual({ email: 'nadie@mail.com' });
    request.flush(null, { status: 204, statusText: 'No Content' });

    expect(text()).toContain('Si el email está registrado, te enviamos un enlace');
    expect(document.activeElement).toBe(el.querySelector('a[href="/login"]'));
  });

  it('does not call the API with an invalid email', () => {
    const { backend, ask, text } = render();
    ask('ana');

    backend.expectNone(`${api}/auth/forgot`);
    expect(text()).toContain('Revisá el email');
  });

  it('explains when the server cannot be reached', () => {
    const { backend, ask, text } = render();
    ask('ana@mail.com');

    backend.expectOne(`${api}/auth/forgot`).error(new ProgressEvent('error'), { status: 0 });

    expect(text()).toContain('No pudimos conectar con el servidor');
    expect(text()).not.toContain('te enviamos un enlace');
  });
});
