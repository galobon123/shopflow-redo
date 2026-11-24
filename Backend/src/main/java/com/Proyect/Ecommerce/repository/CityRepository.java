package com.Proyect.Ecommerce.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.Proyect.Ecommerce.model.City;

public interface CityRepository extends JpaRepository<City, Integer>{
    List<City> findByState_Id(Integer stateId);
}
