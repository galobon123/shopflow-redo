import { Pipe, PipeTransform } from '@angular/core';

const ARS = new Intl.NumberFormat('es-AR', {
  style: 'currency',
  currency: 'ARS',
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

/** Argentine pesos the way the kit writes them: `$ 48.900`, cents only when they are not zero. */
@Pipe({ name: 'ars' })
export class ArsPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    return value == null ? '' : ARS.format(value);
  }
}
