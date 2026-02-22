package io.storeyes.storeyes_coffee.documents.repositories;

import io.storeyes.storeyes_coffee.documents.entities.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    
    /**
     * Find all documents by store ID
     */
    List<Document> findByStore_Id(Long storeId);

    /**
     * Find all documents by store ID and category ID
     */
    List<Document> findByStore_IdAndCategory_Id(Long storeId, Long categoryId);

    /**
     * Find all documents that belong to a category (used when deleting category to unlink)
     */
    List<Document> findByCategory_Id(Long categoryId);
}



