package com.Proyect.Ecommerce.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.Proyect.Ecommerce.model.Address;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long>{
    /** HU-16 · "Mis direcciones": not deleted, the last used in an order first, then the newest. */
    @Query("SELECT a FROM Address a JOIN FETCH a.city c JOIN FETCH c.state "
            + "WHERE a.createdBy.id = :userId AND a.deletedAt IS NULL "
            + "ORDER BY (SELECT MAX(o.createdAt) FROM Order o WHERE o.address = a) DESC NULLS LAST, a.id DESC")
    List<Address> findMine(@Param("userId") Long userId);

    /** HU-16 · Someone else's or a deleted address is just not found: ids don't reveal what exists. */
    Optional<Address> findByIdAndCreatedBy_IdAndDeletedAtIsNull(Long id, Long userId);
}
