import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { GoogleSignIn } from './google-sign-in';
import { testProviders } from '../../testing/test-providers';
import { environment } from '../../../environments/environment';

const api = environment.apiUrl;

describe('GoogleSignIn (HU-32)', () => {
  it('stays hidden, without loading Google, when the server has no client id', async () => {
    TestBed.configureTestingModule({ imports: [GoogleSignIn], providers: testProviders() });
    const fixture = TestBed.createComponent(GoogleSignIn);
    fixture.detectChanges();
    TestBed.tick();

    TestBed.inject(HttpTestingController).expectOne(`${api}/auth/config`).flush({ googleClientId: null });
    await fixture.whenStable();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent?.trim()).toBe('');
    expect(document.querySelector('script[src*="accounts.google.com"]')).toBeNull();
  });
});
