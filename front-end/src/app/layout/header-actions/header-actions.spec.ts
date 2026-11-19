import { TestBed } from '@angular/core/testing';
import { HeaderActions } from './header-actions';
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
});
