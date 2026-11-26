import { TestBed } from '@angular/core/testing';
import { SfStars, formatRating } from './stars';

describe('SfStars (HU-30)', () => {
  function render(rating: number) {
    TestBed.configureTestingModule({ imports: [SfStars] });
    const fixture = TestBed.createComponent(SfStars);
    fixture.componentRef.setInput('rating', rating);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('reads as "4,2 de 5" and fills that share of the stars', () => {
    const el = render(4.2);

    expect(el.getAttribute('role')).toBe('img');
    expect(el.getAttribute('aria-label')).toBe('4,2 de 5 estrellas');
    expect(el.querySelector<HTMLElement>('.sf-stars__fill')!.style.width).toBe('84%');
  });

  it('writes whole ratings without decimals', () => {
    expect(render(4).getAttribute('aria-label')).toBe('4 de 5 estrellas');
    expect(formatRating(3.45)).toBe('3,5');
  });
});
