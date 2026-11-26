import { TestBed } from '@angular/core/testing';
import { HotToastService } from '@ngxpert/hot-toast';
import { Subject } from 'rxjs';
import { Toaster } from './toaster.service';

describe('Toaster', () => {
  function render() {
    const shown: string[] = [];
    const closings: Subject<void>[] = [];
    const toast = (message: string) => {
      shown.push(message);
      const afterClosed = new Subject<void>();
      closings.push(afterClosed);
      return { afterClosed };
    };
    TestBed.configureTestingModule({
      providers: [{ provide: HotToastService, useValue: { success: toast, error: toast, info: toast } }],
    });
    return { toaster: TestBed.inject(Toaster), shown, closings };
  }

  it('shows a message once while it is on screen, without queueing repeats', () => {
    const { toaster, shown, closings } = render();

    toaster.success('Agregado al carrito.');
    toaster.success('Agregado al carrito.');
    toaster.success('Agregado al carrito.');
    expect(shown).toEqual(['Agregado al carrito.']);

    closings[0].next();
    toaster.success('Agregado al carrito.');
    expect(shown).toEqual(['Agregado al carrito.', 'Agregado al carrito.']);
  });

  it('still shows a different message meanwhile', () => {
    const { toaster, shown } = render();

    toaster.success('Agregado a favoritos.');
    toaster.error('No pudimos guardar el favorito.');

    expect(shown).toEqual(['Agregado a favoritos.', 'No pudimos guardar el favorito.']);
  });
});
