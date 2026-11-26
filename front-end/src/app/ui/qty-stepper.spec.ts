import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { SfQtyStepper } from './qty-stepper';
import { Toaster } from '../core/services/toaster.service';

@Component({
  imports: [SfQtyStepper],
  template: `<sf-qty-stepper [value]="value()" [max]="3" (valueChange)="changed($event)" />`,
})
class Host {
  readonly value = signal(1);
  readonly emitted: number[] = [];
  changed(value: number) {
    this.emitted.push(value);
    this.value.set(value);
  }
}

describe('SfQtyStepper', () => {
  function render() {
    const toaster = { info: vi.fn() };
    TestBed.configureTestingModule({ imports: [Host], providers: [{ provide: Toaster, useValue: toaster }] });
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    document.body.appendChild(el);
    const button = (label: string) => el.querySelector<HTMLButtonElement>(`[aria-label="${label}"]`)!;
    const clicks = (label: string, times: number) => {
      button(label).focus();
      for (let i = 0; i < times; i++) {
        button(label).click(); // Enter on a focused button is a click
        fixture.detectChanges();
      }
    };
    const field = () => el.querySelector<HTMLInputElement>('input[aria-label="Cantidad"]')!;
    return { fixture, el, button, clicks, toaster, field };
  }

  it('stops at max without losing the keyboard focus; one more shakes the number and says how many are left', () => {
    const { fixture, el, button, clicks, toaster, field } = render();

    clicks('Sumar uno', 4);

    expect(fixture.componentInstance.emitted).toEqual([2, 3]);
    expect(button('Sumar uno').disabled).toBe(false);
    expect(button('Sumar uno').getAttribute('aria-disabled')).toBe('true');
    expect(document.activeElement).toBe(button('Sumar uno'));
    expect(toaster.info).toHaveBeenCalledWith('Solo quedan 3 en stock.');
    expect(field().classList).toContain('sf-stepper__shake');
    field().dispatchEvent(new Event('animationend'));
    fixture.detectChanges();
    expect(field().classList).not.toContain('sf-stepper__shake');
    el.remove();
  });

  it('takes a typed number, pulled back between min and max; anything else goes back', () => {
    const { fixture, el, toaster } = render();
    const field = el.querySelector<HTMLInputElement>('input[aria-label="Cantidad"]')!;
    const type = (text: string, how: 'change' | 'enter') => {
      field.value = text;
      field.dispatchEvent(how === 'change' ? new Event('change') : new KeyboardEvent('keydown', { key: 'Enter' }));
      fixture.detectChanges();
    };

    type('2', 'enter');
    expect(fixture.componentInstance.emitted).toEqual([2]);
    type('99', 'change');
    expect(fixture.componentInstance.emitted).toEqual([2, 3]);
    expect(field.value).toBe('3');
    expect(toaster.info).toHaveBeenCalledWith('Solo quedan 3 en stock.');
    expect(field.classList).toContain('sf-stepper__shake');
    type('99', 'change'); // already at max: nothing new, but the field shows 3 again
    expect(fixture.componentInstance.emitted).toEqual([2, 3]);
    expect(field.value).toBe('3');
    type('abc', 'change');
    expect(field.value).toBe('3');
    type('0', 'change');
    expect(fixture.componentInstance.emitted).toEqual([2, 3, 1]);
    el.remove();
  });

  it('the up and down arrows change it by one', () => {
    const { fixture, el } = render();
    const field = el.querySelector<HTMLInputElement>('input[aria-label="Cantidad"]')!;
    const press = (key: string) => {
      field.dispatchEvent(new KeyboardEvent('keydown', { key }));
      fixture.detectChanges();
    };

    press('ArrowUp');
    press('ArrowUp');
    press('ArrowDown');
    expect(fixture.componentInstance.emitted).toEqual([2, 3, 2]);
    expect(field.value).toBe('2');
    el.remove();
  });

  it('stops at min without losing the keyboard focus', () => {
    const { fixture, el, button, clicks } = render();
    expect(button('Restar uno').getAttribute('aria-disabled')).toBe('true');

    clicks('Restar uno', 2);

    expect(fixture.componentInstance.emitted).toEqual([]);
    expect(button('Restar uno').disabled).toBe(false);
    expect(document.activeElement).toBe(button('Restar uno'));
    el.remove();
  });
});
