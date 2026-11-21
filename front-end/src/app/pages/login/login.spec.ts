import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { Login } from './login';
import { aToken, testProviders } from '../../testing/test-providers';
import { environment } from '../../../environments/environment';

const api = environment.apiUrl;
const TOKEN = aToken();

describe('Login (HU-07, HU-33)', () => {
  function render(returnUrl?: string, deviceToken?: string) {
    localStorage.clear();
    if (deviceToken) localStorage.setItem('device_token', deviceToken);
    TestBed.configureTestingModule({ imports: [Login], providers: testProviders() });
    const fixture = TestBed.createComponent(Login);
    if (returnUrl) fixture.componentRef.setInput('returnUrl', returnUrl);
    fixture.detectChanges();
    const backend = TestBed.inject(HttpTestingController);
    backend.match(() => true); // requests the stores make on start
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const el = fixture.nativeElement as HTMLElement;

    const signIn = (email = 'ana@mail.com', password = 'secreta123') => {
      for (const [name, value] of [['email', email], ['password', password]]) {
        const input = el.querySelector<HTMLInputElement>(`input[formControlName="${name}"]`)!;
        input.value = value;
        input.dispatchEvent(new Event('input'));
      }
      el.querySelector('form')!.dispatchEvent(new Event('submit'));
    };
    const alert = () => {
      fixture.detectChanges();
      return el.querySelector('[role="alert"]')?.textContent?.trim();
    };
    const typeCode = (code: string) => {
      fixture.detectChanges();
      const input = el.querySelector<HTMLInputElement>('input[formControlName="code"]')!;
      input.value = code;
      input.dispatchEvent(new Event('input'));
      el.querySelector('form')!.dispatchEvent(new Event('submit'));
    };
    return { fixture, el, backend, navigate, signIn, typeCode, alert };
  }

  it('says the credentials are wrong without telling which one', () => {
    const { backend, signIn, alert } = render();
    signIn();

    backend.expectOne(`${api}/auth/login`).flush(
      { status: 401, title: 'No autenticado', detail: 'Email o contraseña incorrectos.' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(alert()).toBe('Email o contraseña incorrectos.');
  });

  it('shows how long to wait after too many attempts', () => {
    const { backend, signIn, alert } = render();
    signIn();

    backend.expectOne(`${api}/auth/login`).flush(
      { status: 429, detail: 'Demasiados intentos. Probá de nuevo en 15 minutos.', retryAfterSeconds: 900 },
      { status: 429, statusText: 'Too Many Requests', headers: { 'Retry-After': '900' } },
    );

    expect(alert()).toBe('Demasiados intentos. Probá de nuevo en 15 minutos.');
  });

  it('does not blame the password when the server is down (HU-03)', () => {
    const { backend, signIn, alert } = render();
    signIn();

    backend.expectOne(`${api}/auth/login`).error(new ProgressEvent('error'), { status: 0 });

    expect(alert()).toContain('No pudimos conectar con el servidor');
  });

  it('explains that the session expired (HU-08)', () => {
    localStorage.clear();
    TestBed.configureTestingModule({ imports: [Login], providers: testProviders() });
    const fixture = TestBed.createComponent(Login);
    fixture.componentRef.setInput('expired', 'true');
    fixture.detectChanges();

    const notice = (fixture.nativeElement as HTMLElement).querySelector('[role="status"]');
    expect(notice?.textContent?.trim()).toBe('Tu sesión venció, iniciá sesión de nuevo.');
  });

  it('goes back to the returnUrl after signing in', () => {
    const { backend, navigate, signIn, alert } = render('/checkout');
    signIn();

    backend.expectOne(`${api}/auth/login`).flush({ token: TOKEN, email: 'ana@mail.com', role: 'USER' });

    expect(alert()).toBeUndefined();
    expect(navigate).toHaveBeenCalledWith('/checkout');
  });

  it('sends the token of this browser, if it has one', () => {
    const { backend, signIn } = render(undefined, 'device-123');
    signIn();

    expect(backend.expectOne(`${api}/auth/login`).request.body).toEqual({
      email: 'ana@mail.com', password: 'secreta123', deviceToken: 'device-123',
    });
  });

  it('asks for the emailed code on a new browser, then signs in and remembers the browser', () => {
    const { fixture, el, backend, navigate, signIn, typeCode } = render('/checkout');
    signIn();

    backend.expectOne(`${api}/auth/login`).flush({ challengeId: 'ch-1', sentTo: 'a***@mail.com' }, { status: 202, statusText: 'Accepted' });
    fixture.detectChanges();
    expect(el.querySelector('h1')?.textContent).toBe('Revisá tu email');
    expect(el.textContent).toContain('a***@mail.com');
    expect(navigate).not.toHaveBeenCalled();

    typeCode('042195');
    const confirm = backend.expectOne(`${api}/auth/login/code`);
    expect(confirm.request.body).toEqual({ challengeId: 'ch-1', code: '042195' });
    confirm.flush({ token: TOKEN, email: 'ana@mail.com', role: 'USER', emailVerified: true, deviceToken: 'device-new' });

    expect(localStorage.getItem('device_token')).toBe('device-new');
    expect(navigate).toHaveBeenCalledWith('/checkout');
  });

  it('shows how many tries are left after a wrong code', () => {
    const { backend, signIn, typeCode, alert } = render();
    signIn();
    backend.expectOne(`${api}/auth/login`).flush({ challengeId: 'ch-1', sentTo: 'a***@mail.com' });

    typeCode('111111');
    backend.expectOne(`${api}/auth/login/code`).flush(
      { status: 400, detail: 'El código no es correcto. Te quedan 4 intentos.' },
      { status: 400, statusText: 'Bad Request' },
    );

    expect(alert()).toBe('El código no es correcto. Te quedan 4 intentos.');
  });

  it('does not send a code that is not 6 digits', () => {
    const { backend, signIn, typeCode } = render();
    signIn();
    backend.expectOne(`${api}/auth/login`).flush({ challengeId: 'ch-1', sentTo: 'a***@mail.com' });

    typeCode('12ab');

    backend.expectNone(`${api}/auth/login/code`);
  });
});
