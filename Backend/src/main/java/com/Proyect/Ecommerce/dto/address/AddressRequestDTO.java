package com.Proyect.Ecommerce.dto.address;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddressRequestDTO {

    @NotBlank(message = "Ingresá el nombre")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String firstName;

    @NotBlank(message = "Ingresá el apellido")
    @Size(max = 100, message = "El apellido no puede superar los 100 caracteres")
    private String lastName;

    @NotBlank(message = "Ingresá la dirección")
    @Pattern(regexp = ".*\\d.*", message = "La dirección tiene que incluir la altura")
    @Size(max = 255, message = "La dirección no puede superar los 255 caracteres")
    private String address;

    @NotNull(message = "Elegí una ciudad")
    private Integer cityId;

    @NotBlank(message = "Ingresá el código postal")
    @Digits(integer = 4, fraction = 0, message = "El código postal no es válido")
    private String zipCode;
}
