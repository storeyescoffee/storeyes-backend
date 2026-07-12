package io.storeyes.storeyes_coffee.charges.repositories;

import io.storeyes.storeyes_coffee.charges.entities.VariableChargeSubCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VariableChargeSubCategoryRepository extends JpaRepository<VariableChargeSubCategory, Long> {

    /** Direct children of a main category (parent_sub_category_id is null). */
    List<VariableChargeSubCategory> findByMainCategoryIdAndParentSubCategoryIdIsNullOrderBySortOrderAsc(Long mainCategoryId);

    /** Children of a sub-category (e.g. Bar, Cuisine, Congelateur, Soda under Raw materials). */
    List<VariableChargeSubCategory> findByParentSubCategoryIdOrderBySortOrderAsc(Long parentSubCategoryId);

    Optional<VariableChargeSubCategory> findByMainCategory_IdAndCodeIgnoreCase(Long mainCategoryId, String code);
}
