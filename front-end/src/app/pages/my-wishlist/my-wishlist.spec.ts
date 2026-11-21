import { testProviders } from '../../testing/test-providers';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BackButton } from "../../components/back-button/back-button";
import { MyWishlist } from './my-wishlist';

describe('MyWishlist', () => {
  let component: MyWishlist;
  let fixture: ComponentFixture<MyWishlist>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MyWishlist, BackButton],
      providers: testProviders()
    })
    .compileComponents();

    fixture = TestBed.createComponent(MyWishlist);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
