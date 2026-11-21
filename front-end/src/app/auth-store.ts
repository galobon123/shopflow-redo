import { errorMessage } from './core/util/error-message';
import { signalStore, withState, withComputed, withMethods, withHooks, patchState } from '@ngrx/signals';
import { computed, inject } from '@angular/core';
import { Observable, tap, map, catchError, throwError } from 'rxjs';
import { UserService } from './core/services/user.service';
import { TokenStorageService } from './core/services/token-storage.service';
import { decodeJwt, isTokenExpired } from './core/util/jwt.util';
import { SignInRequest, SignUpRequest, AuthResponse, LoginChallenge, Role } from './core/models/auth.model';

type AuthState = {
  token: string | null;
  userName: string | null;
  userEmail: string | null;
  role: Role | null;
  /** HU-31 · Without it the person can browse but not buy. */
  emailVerified: boolean;
  loading: boolean;
  error: string | null;
};

const initialState: AuthState = {
  token: null,
  userName: null,
  userEmail: null,
  role: null,
  emailVerified: true,
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
        // Tokens from before HU-31 have no claim: don't nag those accounts (they were marked verified).
        emailVerified: payload.emailVerified ?? true,
        loading: false,
        error: null,
      });
    };

    /** Keeps the session and, when the server just trusted this browser, its device token (HU-33). */
    const startSession = (res: AuthResponse) => {
      tokenStorage.saveToken(res.token);
      if (res.deviceToken) tokenStorage.saveDeviceToken(res.deviceToken);
      setSessionFromToken(res.token);
    };

    /** Runs a call with `loading` on, and leaves the error message in the state if it fails. */
    const withLoading = <T>(call: Observable<T>): Observable<T> => {
      patchState(store, { loading: true, error: null });
      return call.pipe(
        tap(() => patchState(store, { loading: false })),
        catchError((err) => {
          patchState(store, { loading: false, error: errorMessage(err) });
          return throwError(() => err);
        }),
      );
    };

    const toSession = (call: Observable<AuthResponse>): Observable<void> =>
      withLoading(call).pipe(tap(startSession), map(() => undefined));

    return {
      /**
       * Emits null when the session started, or the challenge when this browser must confirm
       * a code sent by email first (HU-33).
       */
      signIn(credentials: SignInRequest): Observable<LoginChallenge | null> {
        const request = { ...credentials, deviceToken: tokenStorage.getDeviceToken() };
        return withLoading(userService.signIn(request)).pipe(
          map((res) => {
            if ('challengeId' in res) return res;
            startSession(res);
            return null;
          }),
        );
      },

      confirmCode(challengeId: string, code: string): Observable<void> {
        return toSession(userService.confirmCode(challengeId, code));
      },

      resendCode(challengeId: string): Observable<LoginChallenge> {
        return userService.resendCode(challengeId);
      },

      signUp(data: SignUpRequest): Observable<void> {
        return toSession(userService.signUp(data));
      },

      /** HU-32 · `credential` is the ID token Google gave the browser. */
      signInWithGoogle(credential: string): Observable<void> {
        return toSession(userService.signInWithGoogle(credential));
      },

      /** HU-31 · The link from the email: confirms it and signs in on this browser. */
      verifyEmail(token: string): Observable<void> {
        return toSession(userService.verifyEmail(token));
      },

      /** HU-31 · Another link. Also refreshes the session: maybe it was confirmed on another device. */
      resendVerification(): Observable<void> {
        return userService.resendVerification().pipe(tap(startSession), map(() => undefined));
      },

      logout(): void {
        tokenStorage.clear();
        patchState(store, { token: null, userEmail: null, userName: null, role: null, emailVerified: true, error: null, loading: false });
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

