package io.storeyes.storeyes_coffee.documents.services;

import io.storeyes.storeyes_coffee.documents.dto.CreateDocumentRequest;
import io.storeyes.storeyes_coffee.documents.dto.DocumentDTO;
import io.storeyes.storeyes_coffee.documents.dto.UpdateDocumentRequest;
import io.storeyes.storeyes_coffee.documents.entities.Document;
import io.storeyes.storeyes_coffee.documents.entities.DocumentCategory;
import io.storeyes.storeyes_coffee.documents.mappers.DocumentMapper;
import io.storeyes.storeyes_coffee.documents.repositories.DocumentCategoryRepository;
import io.storeyes.storeyes_coffee.documents.repositories.DocumentRepository;
import io.storeyes.storeyes_coffee.security.KeycloakTokenUtils;
import io.storeyes.storeyes_coffee.store.entities.Store;
import io.storeyes.storeyes_coffee.store.repositories.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {
    
    private final DocumentRepository documentRepository;
    private final DocumentCategoryRepository documentCategoryRepository;
    private final StoreRepository storeRepository;
    private final DocumentMapper documentMapper;
    private final S3Service s3Service;
    
    /**
     * Get all documents for the current user's store, optionally filtered by category.
     */
    public List<DocumentDTO> getAllDocumentsByStore(Long categoryId) {
        String userId = KeycloakTokenUtils.getUserId();
        if (userId == null) {
            throw new RuntimeException("User is not authenticated");
        }
        Store store = storeRepository.findByOwner_Id(userId)
                .orElseThrow(() -> new RuntimeException("Store not found for current user"));
        List<Document> documents = categoryId != null
                ? documentRepository.findByStore_IdAndCategory_Id(store.getId(), categoryId)
                : documentRepository.findByStore_Id(store.getId());
        return documentMapper.toDTOList(documents);
    }
    
    /**
     * Create a new document and upload file to S3
     */
    @Transactional
    public DocumentDTO createDocument(CreateDocumentRequest request) {
        String userId = KeycloakTokenUtils.getUserId();
        if (userId == null) {
            throw new RuntimeException("User is not authenticated");
        }
        
        Store store = storeRepository.findByOwner_Id(userId)
                .orElseThrow(() -> new RuntimeException("Store not found for current user"));
        
        // Upload file to S3
        String fileUrl = s3Service.uploadFile(request.getFile(), store.getCode());
        
        DocumentCategory category = null;
        if (request.getCategoryId() != null) {
            category = documentCategoryRepository.findById(request.getCategoryId())
                    .filter(c -> c.getStore().getId().equals(store.getId()))
                    .orElse(null);
        }
        
        Document document = Document.builder()
                .store(store)
                .category(category)
                .name(request.getName())
                .description(request.getDescription())
                .url(fileUrl)
                .build();
        
        Document savedDocument = documentRepository.save(document);
        log.info("Document created with ID: {}", savedDocument.getId());
        
        return documentMapper.toDTO(savedDocument);
    }
    
    /**
     * Update an existing document
     */
    @Transactional
    public DocumentDTO updateDocument(Long id, UpdateDocumentRequest request) {
        String userId = KeycloakTokenUtils.getUserId();
        if (userId == null) {
            throw new RuntimeException("User is not authenticated");
        }
        
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + id));
        
        // Verify that the document belongs to the user's store
        Store userStore = storeRepository.findByOwner_Id(userId)
                .orElseThrow(() -> new RuntimeException("Store not found for current user"));
        
        if (!document.getStore().getId().equals(userStore.getId())) {
            throw new RuntimeException("Document does not belong to your store");
        }
        
        // Update name if provided
        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            document.setName(request.getName());
        }
        
        // Update description if provided
        if (request.getDescription() != null) {
            document.setDescription(request.getDescription());
        }
        
        // Update category: explicit unset, or set by id
        if (Boolean.TRUE.equals(request.getUnsetCategory())) {
            document.setCategory(null);
        } else if (request.getCategoryId() != null) {
            DocumentCategory category = documentCategoryRepository.findById(request.getCategoryId())
                    .filter(c -> c.getStore().getId().equals(userStore.getId()))
                    .orElse(null);
            document.setCategory(category);
        }
        
        // Update file if provided
        if (request.getFile() != null && !request.getFile().isEmpty()) {
            // Delete old file from S3
            s3Service.deleteFile(document.getUrl());
            
            // Upload new file to S3
            String newFileUrl = s3Service.uploadFile(request.getFile(), userStore.getCode());
            document.setUrl(newFileUrl);
        }
        
        Document updatedDocument = documentRepository.save(document);
        log.info("Document updated with ID: {}", updatedDocument.getId());
        
        return documentMapper.toDTO(updatedDocument);
    }
    
    /**
     * Delete a document
     */
    @Transactional
    public void deleteDocument(Long id) {
        String userId = KeycloakTokenUtils.getUserId();
        if (userId == null) {
            throw new RuntimeException("User is not authenticated");
        }
        
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + id));
        
        // Verify that the document belongs to the user's store
        Store userStore = storeRepository.findByOwner_Id(userId)
                .orElseThrow(() -> new RuntimeException("Store not found for current user"));
        
        if (!document.getStore().getId().equals(userStore.getId())) {
            throw new RuntimeException("Document does not belong to your store");
        }
        
        // Delete file from S3
        s3Service.deleteFile(document.getUrl());
        
        // Delete document from database
        documentRepository.delete(document);
        log.info("Document deleted with ID: {}", id);
    }
}

