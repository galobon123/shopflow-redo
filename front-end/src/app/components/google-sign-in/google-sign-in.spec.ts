import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { GoogleSignIn, buttonWidth } from './google-sign-in';
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

  it("draws Google's button as wide as the room it gets, between 200 and 400 px", () => {
    expect(buttonWidth(287.6)).toBe(287);
    expect(buttonWidth(150)).toBe(200);
    expect(buttonWidth(900)).toBe(400);
  });
});
