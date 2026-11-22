import { TestBed } from '@angular/core/testing';
import { ProductGallery } from './product-gallery';

const PHOTOS = ['/f/1.jpg', '/f/2.jpg', '/f/3.jpg', '/f/4.jpg'];

describe('ProductGallery (HU-11)', () => {
  // jsdom has no modal dialogs: enough of one to open, close and fire "close" like a browser.
  beforeAll(() => {
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) { this.open = true; };
    HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
      if (!this.open) return;
      this.open = false;
      this.dispatchEvent(new Event('close'));
    };
  });

  function render(images = PHOTOS) {
    TestBed.configureTestingModule({ imports: [ProductGallery] });
    const fixture = TestBed.createComponent(ProductGallery);
    fixture.componentRef.setInput('images', images);
    fixture.componentRef.setInput('name', 'Remera lino');
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const main = () => el.querySelector<HTMLImageElement>('.sf-gallery__main img')?.getAttribute('src');
    const thumbs = () => [...el.querySelectorAll<HTMLButtonElement>('.sf-gallery__thumb')];
    const press = (key: string, on: HTMLElement = el) => {
      on.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }));
      fixture.detectChanges();
    };
    return { fixture, el, main, thumbs, press };
  }

  it('starts on the cover and shows one thumbnail per photo', () => {
    const { el, main, thumbs } = render();

    expect(main()).toBe('/f/1.jpg');
    expect(thumbs()).toHaveLength(4);
    expect(el.textContent).toContain('Foto 1 de 4');
  });

  it('the third thumbnail makes the third photo the main one; the keyboard arrows move from there', () => {
    const { fixture, el, main, thumbs, press } = render();

    thumbs()[2].click();
    fixture.detectChanges();
    expect(main()).toBe('/f/3.jpg');
    expect(thumbs()[2].getAttribute('aria-current')).toBe('true');

    press('ArrowLeft');
    expect(main()).toBe('/f/2.jpg');
    press('ArrowRight');
    press('ArrowRight');
    expect(main()).toBe('/f/4.jpg');
    press('ArrowRight'); // no wrap-around: it stays on the last one
    expect(main()).toBe('/f/4.jpg');
    expect(el.querySelector('[aria-label="Foto siguiente"]')!.getAttribute('aria-disabled')).toBe('true');
  });

  it('on a thumbnail, focus follows the arrow keys', () => {
    const { el, thumbs, press } = render();
    document.body.appendChild(el);

    thumbs()[0].focus();
    press('ArrowRight', thumbs()[0]);

    expect(document.activeElement).toBe(thumbs()[1]);
    el.remove();
  });

  it('the on-screen arrows go to the previous and next photo', () => {
    const { fixture, el, main } = render();
    const next = el.querySelector<HTMLButtonElement>('[aria-label="Foto siguiente"]')!;
    const prev = el.querySelector<HTMLButtonElement>('[aria-label="Foto anterior"]')!;

    expect(prev.getAttribute('aria-disabled')).toBe('true');
    prev.click(); // already on the first one: nothing happens
    fixture.detectChanges();
    expect(main()).toBe('/f/1.jpg');
    next.click();
    fixture.detectChanges();
    expect(main()).toBe('/f/2.jpg');
    prev.click();
    fixture.detectChanges();
    expect(main()).toBe('/f/1.jpg');
  });

  it('focus stays on the arrow after reaching the last photo (it is never really disabled)', () => {
    const { fixture, el, main } = render();
    document.body.appendChild(el);
    const next = el.querySelector<HTMLButtonElement>('[aria-label="Foto siguiente"]')!;

    next.focus();
    for (let i = 0; i < 4; i++) {
      next.click(); // Enter on a focused button is a click
      fixture.detectChanges();
    }

    expect(main()).toBe('/f/4.jpg');
    expect(next.disabled).toBe(false);
    expect(next.getAttribute('aria-disabled')).toBe('true');
    expect(document.activeElement).toBe(next);
    el.remove();
  });

  it('the zoom opens centered on the photo, also when it was already loaded', () => {
    const { fixture, el } = render();
    const dialog = el.querySelector<HTMLDialogElement>('dialog')!;
    const photo = dialog.querySelector<HTMLImageElement>('img')!;
    // jsdom has no layout: a 1200×1600 photo on a 360×640 screen.
    for (const [key, value] of Object.entries({ scrollWidth: 1200, clientWidth: 360, scrollHeight: 1600, clientHeight: 640 })) {
      Object.defineProperty(dialog, key, { value });
    }
    let complete = false;
    Object.defineProperty(photo, 'complete', { get: () => complete });
    const opener = () => el.querySelector<HTMLButtonElement>('.sf-gallery__main')!;

    opener().click();
    expect(dialog.scrollLeft).toBe(0); // still loading
    photo.dispatchEvent(new Event('load'));
    expect(dialog.scrollLeft).toBe(420);
    expect(dialog.scrollTop).toBe(480);

    dialog.close();
    dialog.scrollLeft = dialog.scrollTop = 0;
    complete = true; // cached: no load event this time
    opener().click();
    fixture.detectChanges();
    expect(dialog.scrollLeft).toBe(420);
    expect(dialog.scrollTop).toBe(480);
  });

  it('a swipe to the left shows the next photo; a short one is just a tap', () => {
    const { fixture, el, main } = render();
    const stage = el.querySelector<HTMLElement>('.sf-gallery__stage')!;
    const swipe = (from: number, to: number) => {
      stage.dispatchEvent(Object.assign(new Event('touchstart'), { changedTouches: [{ clientX: from }] }));
      stage.dispatchEvent(Object.assign(new Event('touchend'), { changedTouches: [{ clientX: to }] }));
      fixture.detectChanges();
    };

    swipe(300, 290);
    expect(main()).toBe('/f/1.jpg');
    swipe(300, 120);
    expect(main()).toBe('/f/2.jpg');
    swipe(120, 300);
    expect(main()).toBe('/f/1.jpg');
  });

  it('tapping the main photo opens the zoom with the original file; Esc or a tap outside closes it and focus returns', () => {
    const { fixture, el } = render();
    document.body.appendChild(el);
    const opener = el.querySelector<HTMLButtonElement>('.sf-gallery__main')!;
    const dialog = el.querySelector<HTMLDialogElement>('dialog')!;

    opener.click();
    fixture.detectChanges();
    expect(dialog.open).toBe(true);
    expect(dialog.querySelector('img')?.getAttribute('src')).toBe('/f/1.jpg');

    dialog.click(); // the dark area around the photo
    fixture.detectChanges();
    expect(dialog.open).toBe(false);
    expect(document.activeElement).toBe(opener);

    opener.click();
    dialog.querySelector<HTMLImageElement>('img')!.click(); // the photo itself doesn't close it
    expect(dialog.open).toBe(true);
    dialog.close(); // what the browser does on Esc
    expect(document.activeElement).toBe(opener);
    el.remove();
  });

  it('without photos shows the name in the well, with no arrows or thumbnails', () => {
    const { el, thumbs } = render([]);

    expect(el.querySelector('.sf-gallery__main--empty')?.textContent).toContain('Remera lino');
    expect(thumbs()).toHaveLength(0);
    expect(el.querySelector('[aria-label="Foto siguiente"]')).toBeNull();
  });
});
