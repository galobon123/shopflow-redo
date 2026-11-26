package com.Proyect.Ecommerce.service;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.Proyect.Ecommerce.dto.city.CityResponseDTO;
import com.Proyect.Ecommerce.dto.state.StateResponseDTO;
import com.Proyect.Ecommerce.repository.CityRepository;
import com.Proyect.Ecommerce.repository.StateRepository;

/**
 * HU-16 · Provinces and cities for the address selects, in Spanish alphabetical order:
 * "Córdoba" before "Corrientes" and "Río Cuarto" among the R, whatever the database collation is.
 */
@Service
public class LocationService {

    private final StateRepository stateRepository;
    private final CityRepository cityRepository;

    public LocationService(StateRepository stateRepository, CityRepository cityRepository) {
        this.stateRepository = stateRepository;
        this.cityRepository = cityRepository;
    }

    public List<StateResponseDTO> findStates() {
        return stateRepository.findAll().stream()
                .map(state -> {
                    StateResponseDTO dto = new StateResponseDTO();
                    dto.setId(state.getId());
                    dto.setName(state.getName());
                    return dto;
                })
                .sorted(Comparator.comparing(StateResponseDTO::getName, spanish()))
                .toList();
    }

    /** An unknown province has no cities: an empty list, like a province without any. */
    public List<CityResponseDTO> findCities(Integer stateId) {
        return cityRepository.findByState_Id(stateId).stream()
                .map(city -> {
                    CityResponseDTO dto = new CityResponseDTO();
                    dto.setId(city.getId());
                    dto.setName(city.getName());
                    return dto;
                })
                .sorted(Comparator.comparing(CityResponseDTO::getName, spanish()))
                .toList();
    }

    /** Collator isn't thread-safe: one per call. */
    private static Collator spanish() {
        return Collator.getInstance(Locale.forLanguageTag("es-AR"));
    }
}
