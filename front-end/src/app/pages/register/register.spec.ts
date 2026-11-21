import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { Register } from './register';
import { testProviders } from '../../testing/test-providers';
import { environment } from '../../../environments/environment';

const api = environment.apiUrl;
// header.payload.signature (sub, role, name, far exp): the front only decodes it, never verifies it.
const TOKEN =
  'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbmFAbWFpbC5jb20iLCJyb2xlIjoiUk9MRV9VU0VSIiwibmFtZSI6IkFuYSIsInVzZXJJZCI6MSwiZXhwIjo0MTAyNDQ0ODAwfQ.x';

describe('Register (HU-06)', () => {
  function render(returnUrl?: string) {
    localStorage.clear();
    TestBed.configureTestingModule({ imports: [Register], providers: testProviders() });
    const fixture = TestBed.createComponent(Register);
    if (returnUrl) fixture.componentRef.setInput('returnUrl', returnUrl);
    fixture.detectChanges();
    const backend = TestBed.inject(HttpTestingController);
    backend.match(() => true); // requests the stores make on start
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const el = fixture.nativeElement as HTMLElement;

    const type = (name: string, value: string) => {
      const input = el.querySelector<HTMLInputElement>(`input[formControlName="${name}"]`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    const submit = () => {
      el.querySelector('form')!.dispatchEvent(new Event('submit'));
      fixture.detectChanges();
    };
    const fill = () => {
      type('name', 'Ana');
      type('email', 'ana@mail.com');
      type('password', 'diez-chars');
    };
    return { fixture, el, backend, navigate, fill, submit };
  }

  it('shows the field errors before calling the backend', () => {
    const { el, backend, submit } = render();

    submit();

    expect(el.textContent).toContain('Ingresá tu nombre.');
    expect(el.textContent).toContain('Elegí una contraseña.');
    backend.expectNone(`${api}/auth/register`);
  });

  it('shows the backend error under its field', () => {
    const { fixture, el, backend, fill, submit } = render();
    fill();
    submit();

    backend.expectOne(`${api}/auth/register`).flush(
      { status: 400, title: 'Datos inválidos', errors: { password: 'Tiene que tener al menos 8 caracteres.' } },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();

    const password = el.querySelector('input[formControlName="password"]')!;
    expect(password.getAttribute('aria-invalid')).toBe('true');
    expect(el.textContent).toContain('Tiene que tener al menos 8 caracteres.');
  });

  it('goes back to the returnUrl once the account is created', () => {
    const { backend, navigate, fill, submit } = render('/checkout');
    fill();
    submit();

    backend.expectOne(`${api}/auth/register`).flush({ token: TOKEN, email: 'ana@mail.com', role: 'USER' });

    expect(navigate).toHaveBeenCalledWith('/checkout');
  });

  it('ignores a returnUrl that points outside the store', () => {
    const { backend, navigate, fill, submit } = render('//evil.example.com');
    fill();
    submit();

    backend.expectOne(`${api}/auth/register`).flush({ token: TOKEN, email: 'ana@mail.com', role: 'USER' });

    expect(navigate).toHaveBeenCalledWith('/products/all');
  });
});
