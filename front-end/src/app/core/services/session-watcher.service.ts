import { HttpClient, HttpEventType } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { defer, EMPTY, filter, map, repeat, retry, switchMap, take } from 'rxjs';
import { AuthStore } from '../../auth-store';
import { environment } from '../../../environments/environment';
import { TokenStorageService } from './token-storage.service';

/** The stream closes every so often (server timeout, proxies): open another one. */
export const RECONNECT_MS = 1000;
/** Backend down or no connection: try again calmly. */
export const RETRY_MS = 5000;

/**
 * One open session per account: while signed in, keeps a stream to the backend open, and signs out
 * here the moment the account is opened in another browser (`event:replaced`).
 */
@Injectable({ providedIn: 'root' })
export class SessionWatcher {
  private http = inject(HttpClient);
  private authStore = inject(AuthStore);
  private tokenStorage = inject(TokenStorageService);
  private router = inject(Router);

  constructor() {
    toObservable(this.authStore.token)
      .pipe(
        switchMap((token) => (token ? this.replaced().pipe(map(() => token)) : EMPTY)),
        takeUntilDestroyed(),
      )
      .subscribe((token) => this.signOut(token));
  }

  private replaced() {
    return defer(() =>
      this.http.get(`${environment.apiUrl}/session/events`, {
        observe: 'events',
        reportProgress: true,
        responseType: 'text',
      }),
    ).pipe(
      filter((event) => event.type === HttpEventType.DownloadProgress && !!event.partialText?.includes('event:replaced')),
      take(1),
      repeat({ delay: RECONNECT_MS }),
      retry({ delay: RETRY_MS }),
    );
  }

  private signOut(token: string) {
    // Another tab of this same browser signed in again: that newer session is ours, keep it.
    if (this.tokenStorage.getToken() !== token) {
      this.authStore.initFromStorage();
      return;
    }
    const returnUrl = this.router.url.startsWith('/login') ? undefined : this.router.url;
    this.authStore.logout();
    this.router.navigate(['/login'], { queryParams: { returnUrl, replaced: 'true' } });
  }
}
