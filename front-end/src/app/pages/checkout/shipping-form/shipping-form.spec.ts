import { testProviders } from '../../../testing/test-providers';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ShippingForm } from './shipping-form';

describe('ShippingForm', () => {
  let component: ShippingForm;
  let fixture: ComponentFixture<ShippingForm>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ShippingForm],
      providers: testProviders()
    })
    .compileComponents();

    fixture = TestBed.createComponent(ShippingForm);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
