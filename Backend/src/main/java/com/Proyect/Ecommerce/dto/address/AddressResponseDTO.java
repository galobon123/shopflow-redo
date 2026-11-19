package com.Proyect.Ecommerce.dto.address;

import lombok.Data;

@Data
public class AddressResponseDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String address;
    private String cityName;
    private String stateName;
    private String zipCode;
}
