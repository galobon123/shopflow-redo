import { Directive } from '@angular/core';

@Directive({
  selector: '[appViewPanel]',
  host: {
    class: 'border border-line rounded p-6 bg-surface'
  }
})
export class ViewPanel {

  constructor() { }

}
