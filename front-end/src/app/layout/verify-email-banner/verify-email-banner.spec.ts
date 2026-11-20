import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { VerifyEmailBanner } from './verify-email-banner';
import { aToken, testProviders } from '../../testing/test-providers';
import { environment } from '../../../environments/environment';

const api = environment.apiUrl;

describe('VerifyEmailBanner (HU-31)', () => {
  function render(token: string | null) {
    localStorage.clear();
    if (token) localStorage.setItem('auth_token', token);
    TestBed.configureTestingModule({ imports: [VerifyEmailBanner], providers: testProviders() });
    const fixture = TestBed.createComponent(VerifyEmailBanner);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const text = () => {
      fixture.detectChanges();
      return el.textContent?.trim() ?? '';
    };
    return { el, text, backend: TestBed.inject(HttpTestingController) };
  }

  it('asks an unverified customer to confirm the email', () => {
    const { text } = render(aToken({ emailVerified: false }));

    expect(text()).toContain('Confirmá tu email para poder comprar');
    expect(text()).toContain('ana@mail.com');
  });

  it('is not there for a verified customer or a visitor', () => {
    expect(render(aToken()).text()).toBe('');
    TestBed.resetTestingModule();
    expect(render(null).text()).toBe('');
  });

  it('sends another link, and goes away if the email was confirmed elsewhere', () => {
    const { el, text, backend } = render(aToken({ emailVerified: false }));

    el.querySelector('button')!.click();
    backend.expectOne(`${api}/auth/verify-email/resend`)
      .flush({ token: aToken({ emailVerified: true }), email: 'ana@mail.com', role: 'USER', emailVerified: true });

    expect(text()).toBe('');
  });
});
