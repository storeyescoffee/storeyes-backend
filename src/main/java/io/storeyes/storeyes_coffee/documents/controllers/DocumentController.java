package io.storeyes.storeyes_coffee.documents.controllers;

import io.storeyes.storeyes_coffee.documents.dto.CreateDocumentRequest;
import io.storeyes.storeyes_coffee.documents.dto.DocumentDTO;
import io.storeyes.storeyes_coffee.documents.dto.UpdateDocumentRequest;
import io.storeyes.storeyes_coffee.documents.services.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {
    
    private final DocumentService documentService;
    
    /**
     * Get all documents for the current user's store, optionally filtered by category.
     * GET /api/documents
     * GET /api/documents?categoryId=1
     */
    @GetMapping
    public ResponseEntity<List<DocumentDTO>> getAllDocuments(
            @RequestParam(required = false) Long categoryId) {
        List<DocumentDTO> documents = documentService.getAllDocumentsByStore(categoryId);
        return ResponseEntity.ok(documents);
    }
    
    /**
     * Create a new document and upload file to S3
     * POST /api/documents
     */
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<DocumentDTO> createDocument(
            @Valid @ModelAttribute CreateDocumentRequest request) {
        DocumentDTO document = documentService.createDocument(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(document);
    }
    
    /**
     * Update an existing document
     * PUT /api/documents/{id}
     */
    @PutMapping(value = "/{id}", consumes = "multipart/form-data")
    public ResponseEntity<DocumentDTO> updateDocument(
            @PathVariable Long id,
            @Valid @ModelAttribute UpdateDocumentRequest request) {
        DocumentDTO document = documentService.updateDocument(id, request);
        return ResponseEntity.ok(document);
    }
    
    /**
     * Delete a document
     * DELETE /api/documents/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}

