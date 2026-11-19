package com.Proyect.Ecommerce.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Proyect.Ecommerce.dto.address.AddressRequestDTO;
import com.Proyect.Ecommerce.dto.address.AddressResponseDTO;
import com.Proyect.Ecommerce.service.AddressService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/addresses")
public class AddressController {
    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public ResponseEntity<List<AddressResponseDTO>> findAll(Authentication auth) {
        Long userId = (Long) auth.getCredentials();
        return ResponseEntity.ok(addressService.findAllByUser(userId));
    }

    @PostMapping
    public ResponseEntity<AddressResponseDTO> create(
            @RequestBody @Valid AddressRequestDTO address,
            Authentication auth) {
        Long userId = (Long) auth.getCredentials();
        return ResponseEntity.ok(addressService.save(userId, address));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getCredentials();
        addressService.deleteById(userId, id);
        return ResponseEntity.noContent().build();
    }
}
