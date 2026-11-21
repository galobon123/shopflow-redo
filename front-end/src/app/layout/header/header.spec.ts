import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Header } from './header';
import { testProviders } from '../../testing/test-providers';

describe('Header search (HU-10)', () => {
  it('searches in all products from any page', async () => {
    localStorage.clear();
    TestBed.configureTestingModule({ imports: [Header], providers: testProviders() });
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const el = fixture.nativeElement as HTMLElement;

    const input = el.querySelector<HTMLInputElement>('input[type="search"]')!;
    input.value = '  remera lino ';
    el.querySelector('form')!.dispatchEvent(new Event('submit'));

    expect(navigate).toHaveBeenCalledWith(['/products', 'all'], { queryParams: { q: 'remera lino' } });
  });
});
