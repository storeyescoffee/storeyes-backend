package io.storeyes.storeyes_coffee.documents.controllers;

import io.storeyes.storeyes_coffee.documents.dto.CreateDocumentCategoryRequest;
import io.storeyes.storeyes_coffee.documents.dto.DocumentCategoryDTO;
import io.storeyes.storeyes_coffee.documents.dto.UpdateDocumentCategoryRequest;
import io.storeyes.storeyes_coffee.documents.services.DocumentCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/document-categories")
@RequiredArgsConstructor
public class DocumentCategoryController {

    private final DocumentCategoryService documentCategoryService;

    @GetMapping
    public ResponseEntity<List<DocumentCategoryDTO>> getAllCategories() {
        List<DocumentCategoryDTO> categories = documentCategoryService.getAllCategoriesByStore();
        return ResponseEntity.ok(categories);
    }

    @PostMapping
    public ResponseEntity<DocumentCategoryDTO> createCategory(
            @Valid @RequestBody CreateDocumentCategoryRequest request) {
        DocumentCategoryDTO category = documentCategoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(category);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocumentCategoryDTO> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDocumentCategoryRequest request) {
        DocumentCategoryDTO category = documentCategoryService.updateCategory(id, request);
        return ResponseEntity.ok(category);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        documentCategoryService.deleteCategory(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
