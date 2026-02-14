package io.storeyes.storeyes_coffee.documents.repositories;

import io.storeyes.storeyes_coffee.documents.entities.DocumentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentCategoryRepository extends JpaRepository<DocumentCategory, Long> {

    List<DocumentCategory> findByStore_IdOrderBySortOrderAscNameAsc(Long storeId);
}
