export interface AddressRequest {
    firstName: string;
    lastName: string;
    address: string;
    cityId: number;
    zipCode: string;
}

export interface AddressResponse {
    id: number;
    firstName: string;
    lastName: string;
    address: string;
    cityName: string;
    stateName: string;
    zipCode: string;
}