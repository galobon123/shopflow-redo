import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { adminGuard, authGuard } from './auth.guard';
import { AuthStore } from '../../auth-store';
import { Toaster } from '../services/toaster.service';
import { testProviders } from '../../testing/test-providers';

/** An unsigned JWT the front can decode: it never verifies signatures, the backend does. */
function token(role: 'ROLE_USER' | 'ROLE_ADMIN', expiresInSeconds: number): string {
  const b64 = (o: object) => btoa(JSON.stringify(o)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  const exp = Math.floor(Date.now() / 1000) + expiresInSeconds;
  return `${b64({ alg: 'HS256' })}.${b64({ sub: 'ana@mail.com', role, name: 'Ana', exp })}.x`;
}

describe('auth guards (HU-08)', () => {
  let toaster: { error: ReturnType<typeof vi.fn> };

  function start(storedToken: string | null) {
    localStorage.clear();
    if (storedToken) localStorage.setItem('auth_token', storedToken);
    toaster = { error: vi.fn() };
    TestBed.configureTestingModule({ providers: [testProviders(), { provide: Toaster, useValue: toaster }] });
    return TestBed.inject(AuthStore);
  }

  function run(guard: typeof authGuard, url: string) {
    return TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );
  }

  const serialize = (result: unknown) => TestBed.inject(Router).serializeUrl(result as UrlTree);

  afterEach(() => vi.useRealTimers());

  it('lets a valid session into /cart', () => {
    start(token('ROLE_USER', 3600));
    expect(run(authGuard, '/cart')).toBe(true);
  });

  it('sends a guest to login and back afterwards', () => {
    start(null);
    expect(serialize(run(authGuard, '/cart'))).toBe('/login?returnUrl=%2Fcart');
  });

  it('drops a token that expired with the tab open and says so', () => {
    const authStore = start(token('ROLE_USER', 60));
    expect(authStore.isAuthenticated()).toBe(true);

    vi.useFakeTimers();
    vi.setSystemTime(Date.now() + 120_000);

    expect(serialize(run(authGuard, '/cart'))).toBe('/login?returnUrl=%2Fcart&expired=true');
    expect(authStore.isAuthenticated()).toBe(false); // the header stops showing the user
  });

  it('sends a customer away from admin pages without closing the session', () => {
    const authStore = start(token('ROLE_USER', 3600));

    expect(serialize(run(adminGuard, '/admin/products'))).toBe('/products/all');
    expect(toaster.error).toHaveBeenCalledWith('No tenés permiso para entrar ahí.');
    expect(authStore.isAuthenticated()).toBe(true);
  });

  it('lets an admin into admin pages', () => {
    start(token('ROLE_ADMIN', 3600));
    expect(run(adminGuard, '/admin/products')).toBe(true);
  });
});
