package com.Proyect.Ecommerce.service;

import com.Proyect.Ecommerce.exception.NotFoundException;
import java.util.List;

import org.springframework.stereotype.Service;

import com.Proyect.Ecommerce.dto.address.AddressRequestDTO;
import com.Proyect.Ecommerce.dto.address.AddressResponseDTO;
import com.Proyect.Ecommerce.model.Address;
import com.Proyect.Ecommerce.model.City;
import com.Proyect.Ecommerce.model.User;
import com.Proyect.Ecommerce.repository.AddressRepository;
import com.Proyect.Ecommerce.repository.CityRepository;
import com.Proyect.Ecommerce.repository.UserRepository;

@Service
public class AddressService {
    private final AddressRepository addressRepository;
    private final CityRepository cityRepository;
    private final UserRepository userRepository;

    public AddressService(AddressRepository addressRepository,
                          CityRepository cityRepository,
                          UserRepository userRepository){
        this.addressRepository = addressRepository;
        this.cityRepository = cityRepository;
        this.userRepository = userRepository;
    }

    public List<AddressResponseDTO> findAllByUser(Long userId){
        return addressRepository.findByCreatedBy_Id(userId).stream()
                                                       .map(this::toDto)
                                                       .toList();
    }

    public List<AddressResponseDTO> findAll(){
        return addressRepository.findAll().stream()
                                .map(this::toDto)
                                .toList();
    }

    public void deleteAll(Long userId){
        long deletedCount = addressRepository.deleteByCreatedBy_Id(userId);

        if (deletedCount == 0) {
            throw new NotFoundException("No tenés direcciones guardadas.");
        }
    }

    public AddressResponseDTO save(Long userId, AddressRequestDTO r){
        Address a = new Address();
        City c = cityRepository.findById(r.getCityId())
                               .orElseThrow(() -> new NotFoundException("La ciudad no existe."));
        User u = userRepository.findById(userId)
                               .orElseThrow(() -> new NotFoundException("Usuario no encontrado."));
        a.setFirstName(r.getFirstName());
        a.setLastName(r.getLastName());
        a.setAddress(r.getAddress());
        a.setCity(c);
        a.setCreatedBy(u);
        a.setZipCode(r.getZipCode());

        return toDto(addressRepository.save(a));
    }


    public void deleteById(Long userId,Long id){
        Address a = addressRepository.findById(id)
                                     .orElseThrow(() -> new NotFoundException("Dirección no encontrada."));

        if(!a.getCreatedBy().getId().equals(userId)){
            throw new NotFoundException("Dirección no encontrada.");
        }

        addressRepository.deleteById(id);
    }

    private AddressResponseDTO toDto(Address address){
        AddressResponseDTO dto = new AddressResponseDTO();
        dto.setId(address.getId());
        dto.setFirstName(address.getFirstName());
        dto.setLastName(address.getLastName());
        dto.setAddress(address.getAddress());
        dto.setCityName(address.getCity().getName());
        dto.setStateName(address.getCity().getState().getName());
        dto.setZipCode(address.getZipCode());
        return dto;
    }
}
