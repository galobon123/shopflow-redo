import { TestBed } from '@angular/core/testing';
import { ReviewText } from './review-text';

describe('ReviewText', () => {
  /** The test DOM has no layout: the paragraph says how tall its text is and the observer fires right away. */
  async function render(textHeight: number) {
    vi.stubGlobal('ResizeObserver', class {
      constructor(private readonly callback: () => void) {}
      observe() { this.callback(); }
      disconnect() {}
    });
    vi.spyOn(HTMLElement.prototype, 'scrollHeight', 'get').mockReturnValue(textHeight);
    vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockReturnValue(120);

    const fixture = TestBed.createComponent(ReviewText);
    fixture.componentRef.setInput('text', 'Muy buena.');
    await fixture.whenStable();
    return { el: fixture.nativeElement as HTMLElement, fixture };
  }

  afterEach(() => vi.unstubAllGlobals());

  it('a short comment has no "Ver más"', async () => {
    const { el } = await render(80);
    expect(el.querySelector('button')).toBeNull();
  });

  it('a long one is cut and opens with "Ver más"', async () => {
    const { el, fixture } = await render(400);
    const paragraph = el.querySelector('p')!;
    const button = el.querySelector('button')!;
    expect(paragraph.classList).toContain('sf-review__text--clamped');
    expect(button.textContent?.trim()).toBe('Ver más');
    expect(button.getAttribute('aria-expanded')).toBe('false');

    button.click();
    await fixture.whenStable();

    expect(paragraph.classList).not.toContain('sf-review__text--clamped');
    expect(button.textContent?.trim()).toBe('Ver menos');
    expect(button.getAttribute('aria-expanded')).toBe('true');
  });
});
