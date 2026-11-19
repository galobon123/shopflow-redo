import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { AuthStore } from '../../auth-store';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let logout: ReturnType<typeof vi.fn>;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    logout = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthStore, useValue: { logout } },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  it('keeps the session on 403', () => {
    http.get('/api/admin/orders').subscribe({ error: () => {} });
    backend.expectOne('/api/admin/orders').flush(null, { status: 403, statusText: 'Forbidden' });

    expect(logout).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('logs out and goes to login, remembering the page, on 401 (HU-08)', () => {
    vi.spyOn(TestBed.inject(Router), 'url', 'get').mockReturnValue('/products/all');
    http.get('/api/cart').subscribe({ error: () => {} });
    backend.expectOne('/api/cart').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(logout).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/products/all', expired: 'true' } });
  });

  it('leaves a failed login alone: the login page shows that 401 itself', () => {
    http.post('/api/auth/login', {}).subscribe({ error: () => {} });
    backend.expectOne('/api/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(logout).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });
});
