package com.Proyect.Ecommerce.controller;

import java.time.Duration;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.Proyect.Ecommerce.dto.city.CityResponseDTO;
import com.Proyect.Ecommerce.dto.state.StateResponseDTO;
import com.Proyect.Ecommerce.service.LocationService;

/**
 * HU-16 · Public: the same for everyone and it only changes with a migration,
 * so browsers and proxies may keep it for a day.
 */
@RestController
@RequestMapping("/api")
public class LocationController {

    private static final CacheControl ONE_DAY = CacheControl.maxAge(Duration.ofDays(1)).cachePublic();

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping("/states")
    public ResponseEntity<List<StateResponseDTO>> states() {
        return ResponseEntity.ok().cacheControl(ONE_DAY).body(locationService.findStates());
    }

    @GetMapping("/cities")
    public ResponseEntity<List<CityResponseDTO>> cities(@RequestParam Integer stateId) {
        return ResponseEntity.ok().cacheControl(ONE_DAY).body(locationService.findCities(stateId));
    }
}
