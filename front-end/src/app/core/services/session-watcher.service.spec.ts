import { TestBed } from '@angular/core/testing';
import { HttpEventType } from '@angular/common/http';
import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { aToken, testProviders } from '../../testing/test-providers';
import { environment } from '../../../environments/environment';
import { AuthStore } from '../../auth-store';
import { RECONNECT_MS, SessionWatcher } from './session-watcher.service';

describe('SessionWatcher', () => {
  const STREAM = `${environment.apiUrl}/session/events`;

  function signedIn() {
    localStorage.clear();
    localStorage.setItem('auth_token', aToken());
    TestBed.configureTestingModule({ providers: testProviders() });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    TestBed.inject(SessionWatcher);
    TestBed.tick();
    const backend = TestBed.inject(HttpTestingController);
    return { backend, navigate, store: TestBed.inject(AuthStore) };
  }

  function say(stream: TestRequest, text: string) {
    stream.event({ type: HttpEventType.DownloadProgress, loaded: text.length, partialText: text });
  }

  afterEach(() => vi.useRealTimers());

  it('signs out here as soon as the account is opened in another browser', () => {
    const { backend, navigate, store } = signedIn();

    const stream = backend.expectOne(STREAM);
    say(stream, ':open\n\n');
    expect(store.isAuthenticated()).toBe(true);

    say(stream, ':open\n\nevent:replaced\ndata:Abriste tu cuenta en otro navegador.\n\n');

    expect(store.isAuthenticated()).toBe(false);
    expect(localStorage.getItem('auth_token')).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/', replaced: 'true' } });
  });

  it('keeps a newer session this same browser opened in another tab', () => {
    const { backend, navigate, store } = signedIn();
    const newer = aToken({ name: 'Ana otra pestaña' });
    localStorage.setItem('auth_token', newer);

    say(backend.expectOne(STREAM), 'event:replaced\n\n');

    expect(store.token()).toBe(newer);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('opens the stream again when it closes', () => {
    vi.useFakeTimers();
    const { backend } = signedIn();

    backend.expectOne(STREAM).flush(':open\n\n');
    vi.advanceTimersByTime(RECONNECT_MS);

    backend.expectOne(STREAM);
  });
});
