import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Heart, LucideAngularModule, PackageSearch, ShoppingBag, Trash2 } from 'lucide-angular';
import { ProductResponse } from '../../core/models/product.model';
import { ProductCard } from '../../components/product-card/product-card';
import {
  ArsPipe, SfAlert, SfAlertAction, SfBadge, SfButton, SfEmptyState, SfErrorState, SfField, SfIconButton, SfInput, SfQtyStepper,
  SfSkeleton, SfStockBadge,
} from '../../ui';

const SAMPLE: ProductResponse = {
  id: 0,
  name: 'Sartén de hierro fundido 26 cm',
  description: '',
  price: 48900,
  stock: 3,
  categoryName: 'Cocina',
  active: true,
  createdAt: '',
};

/** Living reference of the UI Kit primitives, with sample data. Public on purpose (portfolio). */
@Component({
  selector: 'app-ui-kit',
  imports: [
    ReactiveFormsModule, LucideAngularModule, ProductCard, ArsPipe, SfAlert, SfAlertAction, SfBadge, SfButton, SfEmptyState, SfErrorState,
    SfField, SfIconButton, SfInput, SfQtyStepper, SfSkeleton, SfStockBadge,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './ui-kit.html',
})
export class UiKit {
  protected readonly icons = { heart: Heart, bag: ShoppingBag, trash: Trash2, search: PackageSearch };
  protected readonly sample = SAMPLE;
  protected readonly prices = [48900, 62500.5, 7630];

  protected readonly qty = signal(1);
  protected readonly favourite = signal(false);

  protected readonly email = new FormControl('lucia.ferreyra@gmail.com', [Validators.required, Validators.email]);
  protected readonly zip = new FormControl('C14', [Validators.required, Validators.pattern(/^(\d{4}|[A-Z]\d{4}[A-Z]{3})$/)]);

  constructor() {
    this.zip.markAsTouched();
  }
}
