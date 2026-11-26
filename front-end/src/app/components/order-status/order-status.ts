import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ORDER_STATUS_LABEL, OrderStatus } from '../../core/models/order.model';
import { SfBadge, SfBadgeTone } from '../../ui';

const TONE: Record<OrderStatus, SfBadgeTone> = {
  PENDIENTE_PAGO: 'low',
  PAGADA: 'info',
  ENVIADA: 'info',
  ENTREGADA: 'stock',
  CANCELADA: 'out',
};

const ZONE = 'America/Argentina/Buenos_Aires';
/** The store's day (dates are instants): the same for every customer, and for the tests in CI's UTC. */
const DAY = new Intl.DateTimeFormat('es-AR', { day: 'numeric', month: 'long', year: 'numeric', timeZone: ZONE });
const DAY_AND_TIME = new Intl.DateTimeFormat('es-AR', {
  day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit', hourCycle: 'h23', timeZone: ZONE,
});

/** "1 de noviembre de 2026". */
export function orderDate(instant: string): string {
  return DAY.format(new Date(instant));
}

/** "1 de noviembre a las 09:30": until when an unpaid order can be paid. */
export function orderDateTime(instant: string): string {
  return DAY_AND_TIME.format(new Date(instant));
}

/** "1 producto", "3 productos". */
export function unitsText(units: number): string {
  return `${units} ${units === 1 ? 'producto' : 'productos'}`;
}

/** HU-19 · An order's status as a badge, in words ("Esperando el pago"); the colour only backs it up. */
@Component({
  selector: 'app-order-status',
  imports: [SfBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<sf-badge [tone]="tone()">{{ label() }}</sf-badge>`,
})
export class OrderStatusBadge {
  readonly status = input.required<OrderStatus>();

  protected readonly tone = computed(() => TONE[this.status()]);
  protected readonly label = computed(() => ORDER_STATUS_LABEL[this.status()]);
}
