package io.storeyes.storeyes_coffee.charges.repositories;

import io.storeyes.storeyes_coffee.charges.entities.PersonnelType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PersonnelTypeRepository extends JpaRepository<PersonnelType, Long> {

    List<PersonnelType> findByStoreIdOrderByNameAsc(Long storeId);

    List<PersonnelType> findByStoreIdAndIsActiveTrueOrderByNameAsc(Long storeId);

    Optional<PersonnelType> findByStoreIdAndId(Long storeId, Long id);

    Optional<PersonnelType> findByStoreIdAndNameIgnoreCase(Long storeId, String name);

    boolean existsByStoreIdAndNameIgnoreCase(Long storeId, String name);
}
