import { Pipe, PipeTransform } from '@angular/core';

const PESOS = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', maximumFractionDigits: 0 });
const PESOS_AND_CENTS = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', minimumFractionDigits: 2 });

/** Argentine pesos the way the kit writes them: `$ 48.900`, and both cent digits when there are any (`$ 62.500,50`). */
@Pipe({ name: 'ars' })
export class ArsPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    if (value == null) return '';
    return Number.isInteger(value) ? PESOS.format(value) : PESOS_AND_CENTS.format(value);
  }
}
