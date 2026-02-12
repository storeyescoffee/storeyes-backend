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
}

