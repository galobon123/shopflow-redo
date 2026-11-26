import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthStore } from '../../auth-store';
import { Toaster } from '../services/toaster.service';
import { TokenStorageService } from '../services/token-storage.service';
import { isTokenExpired } from '../util/jwt.util';

/**
 * HU-08 · True when there is a session that hasn't expired. An expired token is dropped here,
 * so the header stops showing the user before the backend gets the chance to answer 401.
 */
function hasValidSession(): boolean {
  const authStore = inject(AuthStore);
  const token = inject(TokenStorageService).getToken();
  if (authStore.isAuthenticated() && (!token || isTokenExpired(token))) {
    authStore.logout();
    return false;
  }
  return authStore.isAuthenticated();
}

/** To sign in and come back here afterwards; `expired` makes the login explain why. */
function toLogin(returnUrl: string, expired: boolean) {
  return inject(Router).createUrlTree(['/login'], {
    queryParams: { returnUrl, ...(expired ? { expired: 'true' } : {}) },
  });
}

export const authGuard: CanActivateFn = (_route, state) => {
  const hadSession = inject(AuthStore).isAuthenticated();
  return hasValidSession() ? true : toLogin(state.url, hadSession);
};

export const adminGuard: CanActivateFn = (_route, state) => {
  const authStore = inject(AuthStore);
  const hadSession = authStore.isAuthenticated();
  if (!hasValidSession()) return toLogin(state.url, hadSession);
  if (authStore.isAdmin()) return true;

  // A customer keeps the session; they just can't be here.
  inject(Toaster).error('No tenés permiso para entrar ahí.');
  return inject(Router).createUrlTree(['/products/all']);
};

export const guestGuard: CanActivateFn = () => {
  const authStore = inject(AuthStore);
  const router = inject(Router);
  return !authStore.isAuthenticated()
    ? true
    : router.createUrlTree(['/products/all']);
};
