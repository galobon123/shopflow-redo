import { testProviders } from '../../testing/test-providers';
import { ComponentFixture, TestBed } from '@angular/core/testing';

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
