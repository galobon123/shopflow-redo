package com.Proyect.Ecommerce.dto.address;

import lombok.Data;

@Data
public class AddressResponseDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String address;
    /** HU-16 · Ids too, so an edit form can preselect the province and the city. */
    private Integer stateId;
    private Integer cityId;
    private String cityName;
    private String stateName;
    private String zipCode;
}
