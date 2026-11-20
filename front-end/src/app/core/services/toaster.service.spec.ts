import { Component, inject } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HotToastService } from '@ngxpert/hot-toast';
import { Subject } from 'rxjs';
import { Toaster } from './toaster.service';

@Component({
  template: `
    <button id="add" type="button" (click)="add()">Agregar</button>
    <button id="other" type="button" (click)="other()">Otro</button>
    <button id="own" type="button" data-no-toast-lock (click)="add()">Favorito</button>
  `,
})
class TwoButtons {
  private readonly toaster = inject(Toaster);
  adds = 0;
  others = 0;
  add() {
    this.adds++;
    this.toaster.success('Agregado al carrito');
  }
  other() {
    this.others++;
  }
}

describe('Toaster', () => {
  function render() {
    const closings: Subject<void>[] = [];
    const toast = () => {
      const afterClosed = new Subject<void>();
      closings.push(afterClosed);
      return { afterClosed };
    };
    TestBed.configureTestingModule({
      imports: [TwoButtons],
      providers: [{ provide: HotToastService, useValue: { success: toast, error: toast, info: toast } }],
    });
    TestBed.inject(Toaster);
    const fixture = TestBed.createComponent(TwoButtons);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const click = (id: string) => el.querySelector<HTMLButtonElement>(`#${id}`)!.click();
    return { component: fixture.componentInstance, click, closings };
  }

  it('ignores the button that made a toast until that toast closes', () => {
    const { component, click, closings } = render();

    click('add');
    click('add');
    click('add');
    expect(component.adds).toBe(1);

    closings[0].next();
    click('add');
    expect(component.adds).toBe(2);
  });

  it('keeps the other buttons working meanwhile', () => {
    const { component, click } = render();

    click('add');
    click('other');

    expect(component.others).toBe(1);
  });

  it('leaves alone buttons that show their own saving state', () => {
    const { component, click } = render();

    click('own');
    click('own');

    expect(component.adds).toBe(2);
  });
});
