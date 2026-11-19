import { errorMessage } from './core/util/error-message';
import { signalStore, withState, withComputed, withMethods, withHooks, patchState } from '@ngrx/signals';
import { computed, inject } from '@angular/core';
import { Observable, tap, map, catchError, throwError } from 'rxjs';
import { UserService } from './core/services/user.service';
import { TokenStorageService } from './core/services/token-storage.service';
import { decodeJwt, isTokenExpired } from './core/util/jwt.util';
import { SignInRequest, SignUpRequest, AuthResponse, Role } from './core/models/auth.model';

type AuthState = {
  token: string | null;
  userName: string | null;
  userEmail: string | null;
  role: Role | null;
  loading: boolean;
  error: string | null;
};

const initialState: AuthState = {
  token: null,
  userName: null,
  userEmail: null,
  role: null,
  loading: false,
  error: null,
};

export const AuthStore = signalStore(
  { providedIn: 'root' },

  withState(initialState),

  withComputed((state) => ({
    isAuthenticated: computed(() => !!state.token()),
    isAdmin: computed(() => state.role() === 'ROLE_ADMIN'),
  })),

  withMethods((
    store,
    userService = inject(UserService),
    tokenStorage = inject(TokenStorageService)
  ) => {

    // Helper interno para decodificar y mutar el estado con acceso al tokenStorage
    const setSessionFromToken = (token: string) => {
      const payload = decodeJwt(token);

      if (!payload) {
        tokenStorage.clear();
        patchState(store, { token: null, userEmail: null, userName: null, role: null, loading: false });
        return;
      }

      patchState(store, {
        token,
        userEmail: payload.sub,
        userName: payload.name ?? null,
        role: payload.role as Role,
        loading: false,
        error: null,
      });
    };

    return {
      signIn(credentials: SignInRequest): Observable<void> {
        patchState(store, { loading: true, error: null });

        return userService.signIn(credentials).pipe(
          tap((res: AuthResponse) => {
            tokenStorage.saveToken(res.token);
            setSessionFromToken(res.token);
          }),
          map(() => undefined),
          catchError((err) => {
            patchState(store, { loading: false, error: errorMessage(err) });
            return throwError(() => err);
          }),
        );
      },

      signUp(data: SignUpRequest): Observable<void> {
        patchState(store, { loading: true, error: null });

        return userService.signUp(data).pipe(
          tap((res: AuthResponse) => {
            tokenStorage.saveToken(res.token);
            setSessionFromToken(res.token);
          }),
          map(() => undefined),
          catchError((err) => {
            patchState(store, { loading: false, error: errorMessage(err) });
            return throwError(() => err);
          }),
        );
      },

      logout(): void {
        tokenStorage.clear();
        patchState(store, { token: null, userEmail: null, userName: null, role: null, error: null, loading: false });
      },

      initFromStorage(): void {
        const token = tokenStorage.getToken();

        if (token && !isTokenExpired(token)) {
          setSessionFromToken(token);
        } else if (token) {
          tokenStorage.clear();
          patchState(store, { token: null, userEmail: null, userName: null, role: null, error: null, loading: false });
        }
      },
    };
  }),

  withHooks({
    onInit(store) {
      store.initFromStorage();
    },
  })
);

