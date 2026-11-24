package com.Proyect.Ecommerce.dto.address;

import com.Proyect.Ecommerce.model.PostalCodes;

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

    /** HU-16 · The city has to be one of this province's (AddressService checks it). */
    @NotNull(message = "Elegí una provincia")
    private Integer stateId;

    @NotNull(message = "Elegí una ciudad")
    private Integer cityId;

    @NotBlank(message = "Ingresá el código postal")
    @Pattern(regexp = PostalCodes.PATTERN, message = PostalCodes.INVALID)
    private String zipCode;

    /** HU-16 · Validated already normalized: "x5152 abc" is a valid CPA. */
    public void setZipCode(String zipCode) {
        this.zipCode = PostalCodes.normalize(zipCode);
    }
}
