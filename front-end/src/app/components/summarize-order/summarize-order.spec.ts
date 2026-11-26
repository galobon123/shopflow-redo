import { signal } from '@angular/core';
import { aCartItem, testProviders } from '../../testing/test-providers';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { AuthStore } from '../../auth-store';
import { environment } from '../../../environments/environment';

import { SummarizeOrder } from './summarize-order';

describe('SummarizeOrder', () => {
  let component: SummarizeOrder;
  let fixture: ComponentFixture<SummarizeOrder>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SummarizeOrder],
      providers: testProviders()
    })
    .compileComponents();

    fixture = TestBed.createComponent(SummarizeOrder);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

/** HU-17 · Prices already include IVA: nothing is added on top of them. */
describe('SummarizeOrder totals', () => {
  it('totals what the products add up to and says IVA is included', () => {
    // A guest, whatever session another spec left in localStorage: the store reads the cart with GET /cart.
    TestBed.configureTestingModule({
      imports: [SummarizeOrder],
      providers: [testProviders(), { provide: AuthStore, useValue: { isAuthenticated: signal(false) } }],
    });
    const backend = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(SummarizeOrder);
    // The store reads the cart from an effect: let it run so the request is out before answering it.
    TestBed.tick();
    backend.expectOne({ method: 'GET', url: `${environment.apiUrl}/cart` })
      .flush([aCartItem({ productName: 'Taza', productPrice: 1000, quantity: 2 })]);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('.sf-summary__total strong')!.textContent!.replace(/\s/g, ' ')).toBe('$ 2.000');
    expect(el.textContent).toContain('IVA incluido');
    expect(el.textContent).not.toContain('21%');
  });
});
