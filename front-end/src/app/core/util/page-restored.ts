import { DestroyRef, DOCUMENT, inject } from '@angular/core';

/**
 * HU-18 · Runs `restored` when the browser brings this page back from its back/forward cache, e.g. Back from
 * Mercado Pago: the page's state is as it was when it left (a button still waiting), and what it shows may be old.
 * Call it in an injection context; the listener goes away with it.
 */
export function onPageRestored(restored: () => void): void {
  const window = inject(DOCUMENT).defaultView;
  if (!window) return;
  const listener = (event: Event) => {
    if ((event as PageTransitionEvent).persisted) restored();
  };
  window.addEventListener('pageshow', listener);
  inject(DestroyRef).onDestroy(() => window.removeEventListener('pageshow', listener));
}
