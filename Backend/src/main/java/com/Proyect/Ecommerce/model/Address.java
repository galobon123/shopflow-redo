package com.Proyect.Ecommerce.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Data
@Table
@NoArgsConstructor
public class Address {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Ingresá el nombre")
    @Column(nullable = false)
    private String firstName;

    @NotBlank(message = "Ingresá el apellido")
    @Column(nullable = false)
    private String lastName;

    @NotBlank(message = "Ingresá la dirección")
    @Pattern(regexp = ".*\\d.*", message = "La dirección tiene que incluir la altura")
    @Column(nullable = false)
    private String address;

    @NotNull(message = "Elegí una ciudad")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

    /**
     * HU-16 · The CPA rule lives in AddressRequestDTO, not here: addresses saved before it (1 to 4 digits)
     * must still be deletable and editable, and Hibernate would check the rule on every UPDATE.
     */
    @NotBlank(message = "Ingresá el código postal")
    @Column(nullable= false)
    private String zipCode;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    /** HU-16 · Set when the customer deletes it: hidden from "Mis direcciones", kept for the orders sent there. */
    private LocalDateTime deletedAt;
}
