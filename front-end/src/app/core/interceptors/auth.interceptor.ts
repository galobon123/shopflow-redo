import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { TokenStorageService } from '../services/token-storage.service';
import { AuthStore } from '../../auth-store';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const tokenStorage = inject(TokenStorageService);
  const authStore = inject(AuthStore);
  const router = inject(Router);
  const token = tokenStorage.getToken();

  const authReq = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authReq).pipe(
    catchError((err) => {
      // Only a 401 means the session is gone. A 403 is "logged in but not allowed": the caller
      // shows the backend's "No tenés permiso…" and the session stays open.
      if (err.status === 401 && !req.url.includes('/api/auth/')) {
        // HU-08 · Log out everywhere (header included) and come back to this page after signing in.
        const returnUrl = router.url.startsWith('/login') ? undefined : router.url;
        authStore.logout();
        router.navigate(['/login'], { queryParams: { returnUrl, expired: 'true' } });
      }
      return throwError(() => err);
    })
  );
};
