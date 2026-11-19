import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { Login } from './login';
import { testProviders } from '../../testing/test-providers';
import { environment } from '../../../environments/environment';

const api = environment.apiUrl;
// header.payload.signature (sub, role, name, far exp): the front only decodes it, never verifies it.
const TOKEN =
  'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbmFAbWFpbC5jb20iLCJyb2xlIjoiUk9MRV9VU0VSIiwibmFtZSI6IkFuYSIsInVzZXJJZCI6MSwiZXhwIjo0MTAyNDQ0ODAwfQ.x';

describe('Login (HU-07)', () => {
  function render(returnUrl?: string) {
    localStorage.clear();
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
    return { backend, navigate, signIn, alert };
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
});
