import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { testProviders } from '../../../testing/test-providers';
import { AuthStore } from '../../../auth-store';
import { EcommerceStore } from '../../../ecommerce-store';
import { AddressResponse } from '../../../core/models/address.model';
import { environment } from '../../../../environments/environment';
import { ShippingForm } from './shipping-form';

const api = environment.apiUrl;

const STATES = [{ id: 6, name: 'Buenos Aires' }, { id: 14, name: 'Córdoba' }, { id: 82, name: 'Santa Fe' }];
const CORDOBA_CITIES = [{ id: 927, name: 'Córdoba' }, { id: 1279, name: 'Villa Carlos Paz' }];

function aSavedAddress(overrides: Partial<AddressResponse> = {}): AddressResponse {
  return {
    id: 7, firstName: 'Martina', lastName: 'Gómez', address: 'Av. Colón 1234',
    stateId: 14, cityId: 927, cityName: 'Córdoba', stateName: 'Córdoba', zipCode: 'X5000ABC',
    ...overrides,
  };
}

/** HU-16 · Direcciones con provincia, ciudad y CPA. */
describe('ShippingForm', () => {
  let fixture: ComponentFixture<ShippingForm>;
  let component: ShippingForm;
  let backend: HttpTestingController;

  /** A resource takes the answer in a microtask. */
  async function settle() {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  /** `saved` is what GET /api/addresses answers (null: leaves it loading). */
  async function open(saved: AddressResponse[] | null | 'error') {
    TestBed.configureTestingModule({
      imports: [ShippingForm],
      providers: [
        testProviders(),
        { provide: AuthStore, useValue: { isAuthenticated: signal(true), emailVerified: signal(true) } },
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    TestBed.inject(EcommerceStore).loadAddresses();
    fixture = TestBed.createComponent(ShippingForm);
    component = fixture.componentInstance;
    fixture.detectChanges();
    backend.match((r) => r.url.endsWith('/cart/merge')).forEach((r) => r.flush({ items: [], notices: [] }));
    backend.match((r) => r.url.includes('/recommendations')).forEach((r) => r.flush({ sections: [] }));
    backend.match((r) => r.url.endsWith('/wishlist') || r.url.endsWith('/categories')).forEach((r) => r.flush([]));
    const read = backend.expectOne(`${api}/addresses`);
    if (saved === 'error') read.flush({ status: 503 }, { status: 503, statusText: 'Service Unavailable' });
    else if (saved) read.flush(saved);
    fixture.detectChanges();
    backend.match(`${api}/states`).forEach((r) => r.flush(STATES));
    await settle();
    return fixture.nativeElement as HTMLElement;
  }

  const field = (el: HTMLElement, name: string) =>
    el.querySelector<HTMLInputElement & HTMLSelectElement>(`[formControlName="${name}"]`)!;
  const type = (el: HTMLElement, name: string, value: string) => {
    const input = field(el, name);
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
  };
  const pick = (el: HTMLElement, name: string, label: string) => {
    const select = field(el, name);
    select.selectedIndex = [...select.options].findIndex((option) => option.textContent!.trim() === label);
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  };
  const describedText = (el: HTMLElement, name: string) =>
    el.querySelector(`#${field(el, name).getAttribute('aria-describedby')}`)?.textContent?.trim();
  const button = (el: HTMLElement, text: string) =>
    [...el.querySelectorAll('button')].find((b) => b.textContent!.includes(text))!;

  async function chooseVillaCarlosPaz(el: HTMLElement) {
    pick(el, 'stateId', 'Córdoba');
    backend.expectOne(`${api}/cities?stateId=14`).flush(CORDOBA_CITIES);
    await settle();
    pick(el, 'cityId', 'Villa Carlos Paz');
  }

  afterEach(() => backend.verify());

  /** Escenario: Cargar y reutilizar una dirección. */
  it('without saved addresses shows the form right away and ships to the new one, CPA in capitals', async () => {
    const el = await open([]);

    expect(el.querySelector('fieldset')).toBeNull();
    type(el, 'firstName', 'Martina');
    type(el, 'lastName', 'Gómez');
    type(el, 'address', 'Av. San Martín 123');
    await chooseVillaCarlosPaz(el);
    type(el, 'zipCode', 'x5152abc');

    expect(field(el, 'zipCode').value).toBe('X5152ABC');
    expect(component.shipTo()).toEqual({
      firstName: 'Martina', lastName: 'Gómez', address: 'Av. San Martín 123', stateId: 14, cityId: 1279, zipCode: 'X5152ABC',
    });
  });

  it('lists "Mis direcciones" with the first one (the last used) chosen, and ships to it', async () => {
    const el = await open([aSavedAddress({ id: 7 }), aSavedAddress({ id: 8, address: 'Obispo Trejo 312' })]);

    expect(el.querySelector('legend')?.textContent).toBe('Mis direcciones');
    const radios = el.querySelectorAll<HTMLInputElement>('input[type="radio"]');
    expect(radios).toHaveLength(3);
    expect(radios[0].checked).toBe(true);
    expect(el.textContent).toContain('Córdoba, Córdoba · CP X5000ABC');
    expect(el.querySelector('form')).toBeNull();
    expect(component.shipTo()).toBe(7);

    radios[1].click();
    fixture.detectChanges();
    expect(component.shipTo()).toBe(8);
  });

  it('"Agregar nueva" opens the form; an empty one doesn\'t ship and the focus goes to its first field', async () => {
    const el = await open([aSavedAddress()]);

    [...el.querySelectorAll('label')].find((label) => label.textContent!.includes('Agregar nueva'))!.click();
    fixture.detectChanges();

    expect(el.querySelector('form')).not.toBeNull();
    expect(component.shipTo()).toBeNull();
    fixture.detectChanges();
    expect(document.activeElement).toBe(field(el, 'firstName'));
    expect(describedText(el, 'firstName')).toBe('Ingresá el nombre.');
  });

  /** Escenario: CPA inválido. */
  it('says "Código postal inválido" on the field, doesn\'t ship and focuses it', async () => {
    const el = await open([]);
    type(el, 'firstName', 'Martina');
    type(el, 'lastName', 'Gómez');
    type(el, 'address', 'Av. San Martín 123');
    await chooseVillaCarlosPaz(el);
    type(el, 'zipCode', 'ABC');

    expect(field(el, 'zipCode').getAttribute('aria-invalid')).toBe('true');
    expect(describedText(el, 'zipCode')).toBe('Código postal inválido.');
    expect(component.shipTo()).toBeNull();
    expect(document.activeElement).toBe(field(el, 'zipCode'));
  });

  it('accepts the old 4-digit code too', async () => {
    const el = await open([]);
    type(el, 'zipCode', ' 5152 ');

    expect(field(el, 'zipCode').value).toBe('5152');
    expect(field(el, 'zipCode').getAttribute('aria-invalid')).toBeNull();
  });

  it('keeps the city select disabled, saying why, until a province is chosen', async () => {
    const el = await open([]);

    expect(field(el, 'cityId').disabled).toBe(true);
    expect(describedText(el, 'cityId')).toBe('Elegí primero la provincia.');

    pick(el, 'stateId', 'Córdoba');
    expect(describedText(el, 'cityId')).toBe('Cargando ciudades…');
    backend.expectOne(`${api}/cities?stateId=14`).flush(CORDOBA_CITIES);
    await settle();

    expect(field(el, 'cityId').disabled).toBe(false);
    expect(describedText(el, 'cityId')).toBe('¿No está? Elegí la más cercana.'); // still one line: nothing below moves
    expect([...field(el, 'cityId').options].map((option) => option.textContent!.trim()))
      .toEqual(['Elegí una ciudad', 'Córdoba', 'Villa Carlos Paz']);
  });

  it('a new province clears the city chosen before', async () => {
    const el = await open([]);
    await chooseVillaCarlosPaz(el);

    pick(el, 'stateId', 'Santa Fe');
    backend.expectOne(`${api}/cities?stateId=82`).flush([{ id: 3496, name: 'Rosario' }]);
    await settle();

    expect(component.form.controls.cityId.value).toBeNull();
  });

  it('offers to retry when the cities fail to load', async () => {
    const el = await open([]);
    pick(el, 'stateId', 'Córdoba');
    backend.expectOne(`${api}/cities?stateId=14`).flush({ status: 503 }, { status: 503, statusText: 'Service Unavailable' });
    await settle();

    expect(describedText(el, 'cityId')).toBe('No pudimos cargar las ciudades.');
    button(el, 'Reintentar').click();
    fixture.detectChanges();
    backend.expectOne(`${api}/cities?stateId=14`).flush(CORDOBA_CITIES);
    await settle();
    expect(field(el, 'cityId').disabled).toBe(false);
  });

  it('fills in from the browser: autocomplete on every field', async () => {
    const el = await open([]);
    const autocomplete = (name: string) => field(el, name).getAttribute('autocomplete');

    expect(autocomplete('firstName')).toBe('given-name');
    expect(autocomplete('lastName')).toBe('family-name');
    expect(autocomplete('address')).toBe('street-address');
    expect(autocomplete('stateId')).toBe('address-level1');
    expect(autocomplete('cityId')).toBe('address-level2');
    expect(autocomplete('zipCode')).toBe('postal-code');
  });

  it('shows a skeleton while "Mis direcciones" loads, and doesn\'t ship meanwhile', async () => {
    const el = await open(null);

    expect(el.querySelector('[aria-label="Cargando tus direcciones"]')).not.toBeNull();
    expect(component.shipTo()).toBeNull();
  });

  it('shows the error with a retry when "Mis direcciones" fails', async () => {
    const el = await open('error');

    expect(el.querySelector('sf-error-state')?.textContent).toContain('Algo falló de nuestro lado.');
    expect(component.shipTo()).toBeNull();
    button(el, 'Reintentar').click();
    backend.expectOne(`${api}/addresses`).flush([aSavedAddress()]);
    fixture.detectChanges();
    expect(component.shipTo()).toBe(7);
  });
});
