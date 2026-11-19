package com.Proyect.Ecommerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.Proyect.Ecommerce.model.State;

public interface StateRepository extends JpaRepository<State, Integer> {
    
}
