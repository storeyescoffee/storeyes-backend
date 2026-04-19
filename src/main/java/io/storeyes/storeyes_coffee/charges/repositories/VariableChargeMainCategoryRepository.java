package io.storeyes.storeyes_coffee.charges.repositories;

import io.storeyes.storeyes_coffee.charges.entities.VariableChargeMainCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VariableChargeMainCategoryRepository extends JpaRepository<VariableChargeMainCategory, Long> {

    List<VariableChargeMainCategory> findByStoreIdOrderBySortOrderAsc(Long storeId);

    Optional<VariableChargeMainCategory> findByStore_IdAndCodeIgnoreCase(Long storeId, String code);
}
