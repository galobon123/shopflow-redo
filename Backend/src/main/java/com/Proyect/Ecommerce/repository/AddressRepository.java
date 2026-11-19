package com.Proyect.Ecommerce.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Proyect.Ecommerce.model.Address;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long>{
    List<Address> findByCreatedBy_Id(Long UserId);
    long deleteByCreatedBy_Id(Long userId);
}
