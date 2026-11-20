import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { aProduct, aToken, testProviders } from '../../testing/test-providers';
import { ToggleWishlistButton } from './toggle-wishlist-button';
import { environment } from '../../../environments/environment';

describe('ToggleWishlistButton', () => {
  function render() {
    localStorage.clear();
    localStorage.setItem('auth_token', aToken());
    TestBed.configureTestingModule({ imports: [ToggleWishlistButton], providers: testProviders() });
    const fixture = TestBed.createComponent(ToggleWishlistButton);
    fixture.componentRef.setInput('product', aProduct({ id: 15 }));
    fixture.detectChanges();
    const backend = TestBed.inject(HttpTestingController);
    backend.match(() => true); // requests the stores make on start
    const button = (fixture.nativeElement as HTMLElement).querySelector('button')!;
    return { fixture, backend, button };
  }

  it('pulses and ignores taps until the server saves it, then shows it as saved', () => {
    const { fixture, backend, button } = render();

    button.click();
    fixture.detectChanges();
    expect(button.disabled).toBe(true);
    expect(button.getAttribute('aria-busy')).toBe('true');

    button.click();
    const saves = backend.match((req) => req.method === 'POST' && req.url.startsWith(`${environment.apiUrl}/wishlist`));
    expect(saves).toHaveLength(1);

    saves[0].flush({});
    fixture.detectChanges();
    expect(button.disabled).toBe(false);
    expect(button.getAttribute('aria-busy')).toBe('false');
    expect(button.getAttribute('aria-pressed')).toBe('true');
    expect(button.getAttribute('aria-label')).toBe('Quitar de favoritos');
  });

  it('can be tapped again when saving fails', () => {
    const { fixture, backend, button } = render();

    button.click();
    backend
      .expectOne((req) => req.method === 'POST')
      .flush({ detail: 'Error' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(button.disabled).toBe(false);
    expect(button.getAttribute('aria-pressed')).toBe('false');
  });
});
