package io.storeyes.storeyes_coffee.stock.controllers;

import io.storeyes.storeyes_coffee.stock.dto.CreateStockProductRequest;
import io.storeyes.storeyes_coffee.stock.dto.StockProductResponse;
import io.storeyes.storeyes_coffee.stock.dto.UpdateStockProductRequest;
import io.storeyes.storeyes_coffee.stock.services.StockProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock/products")
@RequiredArgsConstructor
public class StockProductController {

    private final StockProductService stockProductService;

    /**
     * List stock products (store-scoped). Optional filters: subCategoryId, search.
     * Deactivated products are excluded unless includeInactive=true (used by the product
     * management screen itself; every other picker keeps getting active-only for free).
     * GET /api/stock/products?subCategoryId=&search=&includeInactive=
     * Used by backoffice and mobile (variable charge product picker, stock screens).
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getProducts(
            @RequestParam(required = false) Long subCategoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        List<StockProductResponse> products = stockProductService.getProducts(subCategoryId, search, includeInactive);
        Map<String, Object> response = new HashMap<>();
        response.put("data", products);
        response.put("message", "Stock products retrieved successfully");
        response.put("timestamp", java.time.OffsetDateTime.now());
        return ResponseEntity.ok(response);
    }

    /**
     * Get stock product by ID.
     * GET /api/stock/products/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getProductById(@PathVariable Long id) {
        StockProductResponse product = stockProductService.getProductById(id);
        Map<String, Object> response = new HashMap<>();
        response.put("data", product);
        response.put("message", "Stock product retrieved successfully");
        response.put("timestamp", java.time.OffsetDateTime.now());
        return ResponseEntity.ok(response);
    }

    /**
     * Create stock product.
     * POST /api/stock/products
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createProduct(
            @Valid @RequestBody CreateStockProductRequest request) {
        StockProductResponse product = stockProductService.createProduct(request);
        Map<String, Object> response = new HashMap<>();
        response.put("data", product);
        response.put("message", "Stock product created successfully");
        response.put("timestamp", java.time.OffsetDateTime.now());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Update stock product.
     * PUT /api/stock/products/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStockProductRequest request) {
        StockProductResponse product = stockProductService.updateProduct(id, request);
        Map<String, Object> response = new HashMap<>();
        response.put("data", product);
        response.put("message", "Stock product updated successfully");
        response.put("timestamp", java.time.OffsetDateTime.now());
        return ResponseEntity.ok(response);
    }

    /**
     * Deactivate a stock product (soft delete). History referencing it (orders, recipes,
     * movements, charges) is kept; it just stops appearing in pickers for new selections.
     * DELETE /api/stock/products/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        stockProductService.deleteProduct(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * Reset stock history for a product (movements + snapshots).
     * The product record itself is preserved.
     * DELETE /api/stock/products/{id}/reset
     */
    @DeleteMapping("/{id}/reset")
    public ResponseEntity<Map<String, Object>> resetProductStock(@PathVariable Long id) {
        stockProductService.resetProductStock(id);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Stock history reset successfully");
        response.put("timestamp", java.time.OffsetDateTime.now());
        return ResponseEntity.ok(response);
    }
}
