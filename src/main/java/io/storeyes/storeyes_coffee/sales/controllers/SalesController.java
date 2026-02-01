package io.storeyes.storeyes_coffee.sales.controllers;

import io.storeyes.storeyes_coffee.sales.dto.ProcessSalesRequest;
import io.storeyes.storeyes_coffee.sales.dto.ProcessSalesResponse;
import io.storeyes.storeyes_coffee.sales.services.SalesProcessingService;
import io.storeyes.storeyes_coffee.store.entities.Store;
import io.storeyes.storeyes_coffee.store.repositories.StoreRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SalesController {
    
    private final SalesProcessingService salesProcessingService;
    private final StoreRepository storeRepository;
    
    /**
     * Trigger async processing of sales for a store and date
     * POST /api/sales/process
     * 
     * Request Body:
     * {
     *   "storeId": 1,
     *   "date": "2025-01-15"
     * }
     * 
     * Returns immediately with status "ACCEPTED" while processing happens asynchronously
     */
    @PostMapping("/process")
    public ResponseEntity<ProcessSalesResponse> processSales(
            @Valid @RequestBody ProcessSalesRequest request) {
        
        // Validate store exists
        Store store = storeRepository.findById(request.getStoreId())
                .orElseThrow(() -> new IllegalArgumentException("Store not found with id: " + request.getStoreId()));
        
        // Trigger async processing
        salesProcessingService.processSalesAsync(store, request.getDate());
        
        // Return immediate response
        ProcessSalesResponse response = ProcessSalesResponse.builder()
                .message("Sales processing started for store " + request.getStoreId() + " on date " + request.getDate())
                .storeId(request.getStoreId())
                .date(request.getDate())
                .status("ACCEPTED")
                .build();
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}

