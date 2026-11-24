package com.Proyect.Ecommerce.service;

import com.Proyect.Ecommerce.exception.BadRequestException;
import com.Proyect.Ecommerce.exception.NotFoundException;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Proyect.Ecommerce.dto.address.AddressRequestDTO;
import com.Proyect.Ecommerce.dto.address.AddressResponseDTO;
import com.Proyect.Ecommerce.model.Address;
import com.Proyect.Ecommerce.model.City;
import com.Proyect.Ecommerce.model.User;
import com.Proyect.Ecommerce.repository.AddressRepository;
import com.Proyect.Ecommerce.repository.CityRepository;
import com.Proyect.Ecommerce.repository.OrderRepository;
import com.Proyect.Ecommerce.repository.UserRepository;

@Service
public class AddressService {
    /** HU-16 · Missing, deleted and someone else's all read the same, so ids don't reveal what exists. */
    private static final String NOT_FOUND = "Dirección no encontrada.";

    private final AddressRepository addressRepository;
    private final CityRepository cityRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public AddressService(AddressRepository addressRepository,
                          CityRepository cityRepository,
                          UserRepository userRepository,
                          OrderRepository orderRepository){
        this.addressRepository = addressRepository;
        this.cityRepository = cityRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
    }

    /** HU-16 · The last used first: the checkout preselects it. */
    @Transactional(readOnly = true)
    public List<AddressResponseDTO> findAllByUser(Long userId){
        return addressRepository.findMine(userId).stream()
                                                 .map(this::toDto)
                                                 .toList();
    }

    @Transactional(readOnly = true)
    public AddressResponseDTO findById(Long userId, Long id){
        return toDto(mine(userId, id));
    }

    @Transactional
    public AddressResponseDTO save(Long userId, AddressRequestDTO r){
        User u = userRepository.findById(userId)
                               .orElseThrow(() -> new NotFoundException("Usuario no encontrado."));
        Address a = new Address();
        a.setCreatedBy(u);
        fill(a, r);
        return toDto(addressRepository.save(a));
    }

    /**
     * HU-16 · An address no order points to is changed in place. One an order was sent to stays as it was
     * (the order keeps showing it): the old one is deleted and the edit is saved as a new address.
     */
    @Transactional
    public AddressResponseDTO update(Long userId, Long id, AddressRequestDTO r){
        Address a = mine(userId, id);
        if (orderRepository.existsByAddressId(id)) {
            a.setDeletedAt(LocalDateTime.now());
            Address copy = new Address();
            copy.setCreatedBy(a.getCreatedBy());
            a = copy;
        }
        fill(a, r);
        return toDto(addressRepository.save(a));
    }

    /** HU-16 · Soft delete: orders sent to this address keep it; "Mis direcciones" doesn't show it anymore. */
    @Transactional
    public void deleteById(Long userId,Long id){
        mine(userId, id).setDeletedAt(LocalDateTime.now());
    }

    private Address mine(Long userId, Long id){
        return addressRepository.findByIdAndCreatedBy_IdAndDeletedAtIsNull(id, userId)
                                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }

    /** HU-16 · The city has to be one of the chosen province's; the zip code comes normalized from the DTO. */
    private void fill(Address a, AddressRequestDTO r){
        City c = cityRepository.findById(r.getCityId())
                               .filter(city -> city.getState().getId().equals(r.getStateId()))
                               .orElseThrow(() -> new BadRequestException("Elegí una ciudad de la provincia elegida."));
        a.setFirstName(r.getFirstName());
        a.setLastName(r.getLastName());
        a.setAddress(r.getAddress());
        a.setCity(c);
        a.setZipCode(r.getZipCode());
    }

    private AddressResponseDTO toDto(Address address){
        AddressResponseDTO dto = new AddressResponseDTO();
        dto.setId(address.getId());
        dto.setFirstName(address.getFirstName());
        dto.setLastName(address.getLastName());
        dto.setAddress(address.getAddress());
        dto.setStateId(address.getCity().getState().getId());
        dto.setCityId(address.getCity().getId());
        dto.setCityName(address.getCity().getName());
        dto.setStateName(address.getCity().getState().getName());
        dto.setZipCode(address.getZipCode());
        return dto;
    }
}
