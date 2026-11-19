import { testProviders } from '../../../testing/test-providers';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ListCartItems } from './list-cart-items';

describe('ListCartItems', () => {
  let component: ListCartItems;
  let fixture: ComponentFixture<ListCartItems>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ListCartItems],
      providers: testProviders()
    })
    .compileComponents();

    fixture = TestBed.createComponent(ListCartItems);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
