package io.storeyes.storeyes_coffee.documents.services;

import io.storeyes.storeyes_coffee.documents.dto.CreateDocumentCategoryRequest;
import io.storeyes.storeyes_coffee.documents.dto.DocumentCategoryDTO;
import io.storeyes.storeyes_coffee.documents.dto.UpdateDocumentCategoryRequest;
import io.storeyes.storeyes_coffee.documents.entities.Document;
import io.storeyes.storeyes_coffee.documents.entities.DocumentCategory;
import io.storeyes.storeyes_coffee.documents.mappers.DocumentCategoryMapper;
import io.storeyes.storeyes_coffee.documents.repositories.DocumentCategoryRepository;
import io.storeyes.storeyes_coffee.documents.repositories.DocumentRepository;
import io.storeyes.storeyes_coffee.security.CurrentStoreContext;
import io.storeyes.storeyes_coffee.store.entities.Store;
import io.storeyes.storeyes_coffee.store.services.StoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentCategoryService {

    private final DocumentCategoryRepository categoryRepository;
    private final DocumentRepository documentRepository;
    private final StoreService storeService;
    private final DocumentCategoryMapper categoryMapper;

    @Transactional(readOnly = true)
    public List<DocumentCategoryDTO> getAllCategoriesByStore() {
        Store store = getCurrentStore();
        List<DocumentCategory> categories = categoryRepository.findByStore_IdOrderBySortOrderAscNameAsc(store.getId());
        return categoryMapper.toDTOList(categories);
    }

    @Transactional
    public DocumentCategoryDTO createCategory(CreateDocumentCategoryRequest request) {
        Store store = getCurrentStore();

        Integer sortOrder = request.getSortOrder() != null ? request.getSortOrder() : 0;
        DocumentCategory category = DocumentCategory.builder()
                .store(store)
                .name(request.getName().trim())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .sortOrder(sortOrder)
                .build();
        DocumentCategory saved = categoryRepository.save(category);
        log.info("Document category created with ID: {}", saved.getId());
        return categoryMapper.toDTO(saved);
    }

    @Transactional
    public DocumentCategoryDTO updateCategory(Long id, UpdateDocumentCategoryRequest request) {
        Store userStore = getCurrentStore();
        DocumentCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document category not found with id: " + id));
        if (!category.getStore().getId().equals(userStore.getId())) {
            throw new RuntimeException("Category does not belong to your store");
        }

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            category.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            category.setDescription(request.getDescription().trim().isEmpty() ? null : request.getDescription().trim());
        }
        if (request.getSortOrder() != null) {
            category.setSortOrder(request.getSortOrder());
        }
        DocumentCategory updated = categoryRepository.save(category);
        log.info("Document category updated with ID: {}", updated.getId());
        return categoryMapper.toDTO(updated);
    }

    @Transactional
    public void deleteCategory(Long id) {
        Store userStore = getCurrentStore();
        DocumentCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document category not found with id: " + id));
        if (!category.getStore().getId().equals(userStore.getId())) {
            throw new RuntimeException("Category does not belong to your store");
        }
        // Unlink all documents from this category before deleting
        List<Document> documentsInCategory = documentRepository.findByCategory_Id(id);
        for (Document doc : documentsInCategory) {
            doc.setCategory(null);
        }
        documentRepository.saveAll(documentsInCategory);
        categoryRepository.delete(category);
        log.info("Document category deleted with ID: {}", id);
    }

    private Store getCurrentStore() {
        Long storeId = CurrentStoreContext.getCurrentStoreId();
        if (storeId == null) {
            throw new RuntimeException("Store context not found for current user");
        }
        return storeService.getStoreEntityById(storeId);
    }
}
