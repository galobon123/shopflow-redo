import { testProviders, aCartItem } from '../../testing/test-providers';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ShowCartItem } from './show-cart-item';

describe('ShowCartItem', () => {
  let component: ShowCartItem;
  let fixture: ComponentFixture<ShowCartItem>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ShowCartItem],
      providers: testProviders()
    })
    .compileComponents();

    fixture = TestBed.createComponent(ShowCartItem);
    fixture.componentRef.setInput('item', aCartItem());
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
