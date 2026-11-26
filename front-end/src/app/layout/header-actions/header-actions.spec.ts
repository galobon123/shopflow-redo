import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HeaderActions } from './header-actions';
import { AuthStore } from '../../auth-store';
import { testProviders } from '../../testing/test-providers';

describe('HeaderActions', () => {
  it('offers to sign in or create an account to a guest', async () => {
    localStorage.clear();
    TestBed.configureTestingModule({ imports: [HeaderActions], providers: testProviders() });
    const fixture = TestBed.createComponent(HeaderActions);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.textContent).toContain('Ingresar');
    expect(el.textContent).toContain('Crear cuenta');
    expect(el.querySelector('[aria-label^="Carrito, 0"]')).not.toBeNull();
  });

  /** HU-19 · The account menu leads to "Mis pedidos". */
  it('has "Mis pedidos" in the account menu', async () => {
    TestBed.configureTestingModule({
      imports: [HeaderActions],
      providers: [
        ...testProviders(),
        {
          provide: AuthStore,
          useValue: {
            isAuthenticated: signal(true), isAdmin: signal(false), userName: signal('Ana Gómez'),
            userEmail: signal('ana@mail.com'), emailVerified: signal(true),
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(HeaderActions);
    await fixture.whenStable();

    (fixture.nativeElement.querySelector('.sf-profile-btn') as HTMLButtonElement).click();
    await fixture.whenStable();

    const orders = [...document.querySelectorAll('.sf-menu a')].find((a) => a.textContent?.includes('Mis pedidos'));
    expect(orders?.getAttribute('href')).toBe('/account/orders');
  });
});
