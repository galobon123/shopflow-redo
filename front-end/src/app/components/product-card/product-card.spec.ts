import { TestBed } from '@angular/core/testing';
import { ProductCard } from './product-card';
import { aProduct, testProviders } from '../../testing/test-providers';
import { ProductResponse } from '../../core/models/product.model';

describe('ProductCard', () => {
  function render(product: ProductResponse) {
    TestBed.configureTestingModule({ imports: [ProductCard], providers: testProviders() });
    const fixture = TestBed.createComponent(ProductCard);
    fixture.componentRef.setInput('product', product);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows name, category, price in pesos and low stock', () => {
    const el = render(aProduct({ stock: 3 }));

    expect(el.textContent).toContain('Remera lino');
    expect(el.textContent).toContain('Remeras');
    expect(el.textContent).toMatch(/\$\s?48\.900/);
    expect(el.textContent).toContain('Quedan 3');
  });

  it('disables "Agregar" when there is no stock', () => {
    const el = render(aProduct({ stock: 0 }));
    const add = [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Agregar'));

    expect(el.textContent).toContain('Sin stock');
    expect(add?.disabled).toBe(true);
  });
});
