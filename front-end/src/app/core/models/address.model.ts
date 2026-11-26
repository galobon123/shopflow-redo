export interface AddressRequest {
    firstName: string;
    lastName: string;
    address: string;
    /** HU-16 · The backend checks the city is one of this province's. */
    stateId: number;
    cityId: number;
    zipCode: string;
}

export interface AddressResponse {
    id: number;
    firstName: string;
    lastName: string;
    address: string;
    stateId: number;
    cityId: number;
    cityName: string;
    stateName: string;
    zipCode: string;
}

/** HU-16 · Province and city options for the address form, already in alphabetical order. */
export interface StateResponse {
    id: number;
    name: string;
}

export interface CityResponse {
    id: number;
    name: string;
}

/** HU-16 · The CPA ("X5152ABC") or the old 4-digit code, as the backend accepts it once normalized. */
export const POSTAL_CODE = /^([A-Z]\d{4}[A-Z]{3}|\d{4})$/;

/** " x5152 abc " → "X5152ABC", the same as the backend saves it. */
export function normalizePostalCode(code: string): string {
    return code.replace(/\s/g, '').toUpperCase();
}
