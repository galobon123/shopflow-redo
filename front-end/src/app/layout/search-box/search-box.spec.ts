import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { SearchBox, SUGGEST_DELAY_MS } from './search-box';
import { ProductSuggestion } from '../../core/models/product.model';
import { testProviders } from '../../testing/test-providers';
import { environment } from '../../../environments/environment';

const suggestionsUrl = `${environment.apiUrl}/products/suggestions`;

const SARTEN: ProductSuggestion = { id: 1, name: 'Sartén de hierro fundido 26 cm', price: 48900, imageUrl: 'https://cdn.example/sarten.jpg' };
const OLLA: ProductSuggestion = { id: 2, name: 'Olla de acero 24 cm', price: 62500, imageUrl: null };

describe('Search box suggestions', () => {
  async function render() {
    TestBed.configureTestingModule({ imports: [SearchBox], providers: testProviders() });
    const fixture = TestBed.createComponent(SearchBox);
    await fixture.whenStable();
    const backend = TestBed.inject(HttpTestingController);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const el = fixture.nativeElement as HTMLElement;
    const input = el.querySelector<HTMLInputElement>('input[type="search"]')!;

    /** Types like a person and waits for the pause after the last key. */
    const type = async (text: string) => {
      input.value = text;
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      await new Promise((resolve) => setTimeout(resolve, SUGGEST_DELAY_MS + 20));
      fixture.detectChanges();
    };
    const key = (name: string) => {
      input.dispatchEvent(new KeyboardEvent('keydown', { key: name }));
      fixture.detectChanges();
    };
    const options = () => [...el.querySelectorAll<HTMLElement>('[role="option"]')];
    return { fixture, el, backend, navigate, input, type, key, options };
  }

  it('waits for 3 letters before asking', async () => {
    const { backend, type, options } = await render();

    await type('sa');

    backend.expectNone((req) => req.url === suggestionsUrl);
    expect(options()).toHaveLength(0);
  });

  it('asks once after the pause and shows photo, name and price', async () => {
    const { fixture, backend, type, options, input } = await render();

    await type('sarten');
    backend.expectOne((req) => req.url === suggestionsUrl && req.params.get('q') === 'sarten').flush([SARTEN, OLLA]);
    fixture.detectChanges();

    expect(input.getAttribute('aria-expanded')).toBe('true');
    expect(options().map((o) => o.textContent)).toEqual([
      expect.stringContaining('Sartén de hierro fundido 26 cm'),
      expect.stringContaining('Olla de acero 24 cm'),
    ]);
    expect(options()[0].textContent).toMatch(/\$\s?48\.900/);
    expect(options()[0].querySelector('img')?.getAttribute('src')).toBe('https://cdn.example/sarten.jpg');
    expect(options()[1].querySelector('img')).toBeNull();
  });

  it('opens the page of the suggestion picked with the arrows and Enter (HU-11)', async () => {
    const { fixture, el, backend, navigate, type, key } = await render();
    await type('olla');
    backend.expectOne((req) => req.url === suggestionsUrl).flush([SARTEN, OLLA]);
    fixture.detectChanges();

    key('ArrowDown');
    key('ArrowDown');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));

    expect(navigate).toHaveBeenCalledWith(['/product', OLLA.id]);
  });

  it('closes with Escape and says so when nothing matches', async () => {
    const { fixture, el, backend, type, key, options } = await render();

    await type('zzzz');
    backend.expectOne((req) => req.url === suggestionsUrl).flush([]);
    fixture.detectChanges();
    expect(el.querySelector('.sf-suggest')?.textContent).toContain('No encontramos productos para «zzzz»');

    key('Escape');
    expect(el.querySelector('.sf-suggest')).toBeNull();
    expect(options()).toHaveLength(0);
  });
});
