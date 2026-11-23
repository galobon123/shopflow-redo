import { testProviders } from '../../testing/test-providers';
import { TestBed } from '@angular/core/testing';
import { Location } from '@angular/common';
import { Router, provideRouter } from '@angular/router';

import { BackButton } from './back-button';

describe('BackButton', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [BackButton],
      providers: [...testProviders(), provideRouter([{ path: '**', children: [] }])],
    });
  });

  async function render(navigateTo?: string) {
    const fixture = TestBed.createComponent(BackButton);
    if (navigateTo) fixture.componentRef.setInput('navigateTo', navigateTo);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('links to a fixed page when told where', async () => {
    const el = await render('/cart');
    expect(el.querySelector('a')?.getAttribute('href')).toBe('/cart');
  });

  it('goes back to the previous screen of the shop', async () => {
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/a');
    await router.navigateByUrl('/b');
    const el = await render();
    const back = vi.spyOn(TestBed.inject(Location), 'back').mockImplementation(() => {});

    el.querySelector('button')!.click();

    expect(back).toHaveBeenCalled();
  });

  it('opened from a link, goes to the catalogue instead of leaving the shop', async () => {
    const el = await render();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const back = vi.spyOn(TestBed.inject(Location), 'back');

    el.querySelector('button')!.click();

    expect(navigate).toHaveBeenCalledWith('/products/all');
    expect(back).not.toHaveBeenCalled();
  });
});
