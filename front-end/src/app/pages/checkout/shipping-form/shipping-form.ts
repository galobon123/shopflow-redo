import { ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, computed, effect, inject, linkedSignal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatIcon } from '@angular/material/icon';
import { ViewPanel } from '../../../directives/view-panel';
import { EcommerceStore } from '../../../ecommerce-store';
import { AddressService } from '../../../core/services/address.service';
import { AddressRequest, POSTAL_CODE, normalizePostalCode } from '../../../core/models/address.model';
import { SfButton, SfErrorState, SfField, SfInput, SfSkeleton } from '../../../ui';

/** HU-16 · Valid once normalized, so "x5152 abc" passes before leaving the field. Empty is `required`'s job. */
function postalCode(control: AbstractControl<string>): ValidationErrors | null {
  return !control.value || POSTAL_CODE.test(normalizePostalCode(control.value)) ? null : { postalCode: true };
}

/** "Agregar nueva" in the list of saved addresses. */
const NEW = 'new';

/**
 * HU-16 · Where the order goes: one of "Mis direcciones" (the last used comes chosen) or a new one,
 * with the province and city from lists. Without saved addresses, the form shows right away.
 */
@Component({
  selector: 'app-shipping-form',
  imports: [ViewPanel, MatIcon, ReactiveFormsModule, SfButton, SfErrorState, SfField, SfInput, SfSkeleton],
  templateUrl: './shipping-form.html',
  styleUrl: './shipping-form.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShippingForm {
  private fb = inject(FormBuilder);
  private addressService = inject(AddressService);
  private host = inject<ElementRef<HTMLElement>>(ElementRef);
  private cdr = inject(ChangeDetectorRef);
  readonly store = inject(EcommerceStore);

  form = this.fb.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    address: ['', [Validators.required, Validators.maxLength(255), Validators.pattern(/\d/)]],
    stateId: this.fb.control<number | null>(null, Validators.required),
    cityId: this.fb.control<number | null>(null, Validators.required),
    zipCode: ['', [Validators.required, postalCode]],
  });

  protected readonly NEW = NEW;

  /** Saved address id or NEW. Follows the list: the first one (last used) once it loads, or a just saved one. */
  protected readonly choice = linkedSignal<number | typeof NEW>(() => this.store.addresses()[0]?.id ?? NEW);
  protected readonly showForm = computed(() => this.choice() === NEW);

  private readonly stateId = toSignal(this.form.controls.stateId.valueChanges, { initialValue: null });
  protected readonly states = rxResource({ stream: () => this.addressService.getStates() });
  /** Idle (no request) until a province is chosen. */
  protected readonly cities = rxResource({
    params: () => this.stateId() ?? undefined,
    stream: ({ params }) => this.addressService.getCities(params),
  });
  /**
   * The city select waits for the province and its cities; the hint under it says why.
   * Always one line of text, so choosing the province doesn't move the rest of the form.
   */
  protected readonly citiesReady = computed(() => this.cities.hasValue() && !this.cities.isLoading());
  protected readonly cityHint = computed(() => {
    if (!this.stateId()) return 'Elegí primero la provincia.';
    if (this.cities.isLoading()) return 'Cargando ciudades…';
    if (this.cities.error()) return 'No pudimos cargar las ciudades.';
    return '¿No está? Elegí la más cercana.';
  });

  constructor() {
    // Another province: the city chosen before is not one of its cities.
    this.form.controls.stateId.valueChanges.subscribe(() => this.form.controls.cityId.reset(null));
    // Each select waits for its options (the province's, for the city).
    effect(() => {
      const { stateId, cityId } = this.form.controls;
      if (this.states.hasValue()) stateId.enable({ emitEvent: false });
      else stateId.disable({ emitEvent: false });
      if (this.citiesReady()) cityId.enable({ emitEvent: false });
      else cityId.disable({ emitEvent: false });
    });
  }

  protected choose(choice: number | typeof NEW) {
    this.choice.set(choice);
  }

  /** Shown as it will be saved: "x5152 abc" → "X5152ABC". */
  protected normalizeZip() {
    const zip = this.form.controls.zipCode;
    if (zip.value) zip.setValue(normalizePostalCode(zip.value));
  }

  /**
   * What the order ships to: a saved address id, or the new address once it's valid.
   * Null while "Mis direcciones" loads or failed, or when a field is wrong: then the first wrong one gets the focus.
   */
  shipTo(): number | AddressRequest | null {
    if (this.store.addressesLoading() || this.store.addressesError()) return null;
    const choice = this.choice();
    if (choice !== NEW) return choice;

    this.normalizeZip();
    // A select waiting for its options is disabled, so it doesn't count as invalid: it can't ship either.
    if (this.form.invalid || this.form.controls.cityId.disabled) {
      this.form.markAllAsTouched();
      this.cdr.markForCheck();
      this.focusFirstInvalid();
      return null;
    }
    const value = this.form.getRawValue();
    return {
      firstName: value.firstName!.trim(),
      lastName: value.lastName!.trim(),
      address: value.address!.trim(),
      stateId: value.stateId!,
      cityId: value.cityId!,
      zipCode: value.zipCode!,
    };
  }

  private focusFirstInvalid() {
    // No invalid field: the city is still waiting for its options, so back to the province.
    const name = Object.keys(this.form.controls).find((key) => this.form.get(key)!.invalid) ?? 'stateId';
    this.host.nativeElement.querySelector<HTMLElement>(`[formControlName="${name}"]`)?.focus();
  }
}
