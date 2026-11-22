import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { SfQtyStepper } from './qty-stepper';

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
    TestBed.configureTestingModule({ imports: [Host] });
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
    return { fixture, el, button, clicks };
  }

  it('stops at max without losing the keyboard focus', () => {
    const { fixture, el, button, clicks } = render();

    clicks('Sumar uno', 4);

    expect(fixture.componentInstance.emitted).toEqual([2, 3]);
    expect(button('Sumar uno').disabled).toBe(false);
    expect(button('Sumar uno').getAttribute('aria-disabled')).toBe('true');
    expect(document.activeElement).toBe(button('Sumar uno'));
    expect(el.textContent).toContain('Quedan 3 en stock');
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
