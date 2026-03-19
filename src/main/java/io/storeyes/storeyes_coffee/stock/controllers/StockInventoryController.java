package io.storeyes.storeyes_coffee.stock.controllers;

import io.storeyes.storeyes_coffee.stock.dto.ManualConsumptionRequest;
import io.storeyes.storeyes_coffee.stock.dto.SetStockRequest;
import io.storeyes.storeyes_coffee.stock.dto.SupplementStockItemRequest;
import io.storeyes.storeyes_coffee.stock.dto.ValidateInventoryRequest;
import io.storeyes.storeyes_coffee.stock.dto.StockInventoryItemResponse;
import io.storeyes.storeyes_coffee.stock.dto.StockToBuyItemResponse;
import io.storeyes.storeyes_coffee.stock.services.StockMovementService;
import io.storeyes.storeyes_coffee.stock.services.StockSalesSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock/inventory")
@RequiredArgsConstructor
public class StockInventoryController {

    private final StockMovementService stockMovementService;
    private final StockSalesSyncService stockSalesSyncService;

    /**
     * Inventory summary: all store products with current quantity and total value (based on movements).
     * Products with no movements have estimatedQuantity=0.
     * GET /api/stock/inventory
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getInventorySummary() {
        List<StockInventoryItemResponse> items = stockMovementService.getInventorySummary();
        Map<String, Object> response = new HashMap<>();
        response.put("data", items);
        response.put("message", "Inventory summary retrieved successfully");
        response.put("timestamp", java.time.OffsetDateTime.now());
        return ResponseEntity.ok(response);
    }

    /**
     * Products that need restocking (current quantity &lt;= minimal threshold).
     * For To Buy screen. Returns list grouped by subCategory when ordered.
     * GET /api/stock/inventory/to-buy
     */
    @GetMapping("/to-buy")
    public ResponseEntity<Map<String, Object>> getToBuyList() {
        List<StockToBuyItemResponse> items = stockMovementService.getToBuyList();
        Map<String, Object> response = new HashMap<>();
        response.put("data", items);
        response.put("message", "To buy list retrieved successfully");
        response.put("timestamp", java.time.OffsetDateTime.now());
        return ResponseEntity.ok(response);
    }

    /**
     * Set current stock for a product (creates an ADJUSTMENT movement so total quantity = quantityInBaseUnit).
     * Use for opening stock or manual correction. Quantity must be in the product's base unit (e.g. g, cl, piece).
     * POST /api/stock/inventory/set
     * Body: { "productId": 7, "quantityInBaseUnit": 2000 }
     */
    @PostMapping("/set")
    public ResponseEntity<Map<String, Object>> setStock(@Valid @RequestBody SetStockRequest request) {
        stockMovementService.setStockQuantity(request);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Stock quantity set successfully");
        response.put("timestamp", java.time.OffsetDateTime.now());
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Supplement stock: record goods received outside the normal purchase flow.
     * Creates an ADJUSTMENT movement for each item with a positive delta, updating estimated stock immediately.
     * Products with no delta (or delta = 0) are silently skipped.
     * POST /api/stock/inventory/supplement
     * Body: [{ "productId": 7, "deltaCountingQuantity": 2, "amount": 150 }, ...]
     */
    @PostMapping("/supplement")
    public ResponseEntity<Map<String, Object>> supplementStock(
            @Valid @RequestBody List<SupplementStockItemRequest> items) {
        stockMovementService.supplementStock(items);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Stock supplement recorded successfully");
        response.put("timestamp", java.time.OffsetDateTime.now());
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Save physical inventory counts without creating ADJUSTMENT movements.
     * This is the "Fill out the form" / "Save" step: it updates real quantities and values used for variance,
     * but does not reset estimated stock. The reset happens only when /validate is called.
     * POST /api/stock/inventory/count
     */
    @PostMapping("/count")
    public ResponseEntity<Map<String, Object>> countInventory(
            @Valid @RequestBody ValidateInventoryRequest request) {
        stockMovementService.saveInventoryCounts(request);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Inventory counts saved successfully");
        response.put("timestamp", java.time.OffsetDateTime.now());
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Batch validate inventory: create session, snapshots, ADJUSTMENT movements.
     * Use when owner accepts physical counts as new baseline (Accept and validate).
     * POST /api/stock/inventory/validate
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateInventory(@Valid @RequestBody ValidateInventoryRequest request) {
        stockMovementService.validateInventory(request);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Inventory validated successfully");
        response.put("timestamp", java.time.OffsetDateTime.now());
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Record manual consumption (waste, spillage, etc.). Creates a CONSUMPTION movement.
     * POST /api/stock/inventory/consumption
     */
    @PostMapping("/consumption")
    public ResponseEntity<Map<String, Object>> recordConsumption(
            @Valid @RequestBody ManualConsumptionRequest request) {
        stockMovementService.createManualConsumption(request);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Consumption recorded successfully");
        response.put("timestamp", java.time.OffsetDateTime.now());
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Apply daily sales (SalesProduct rows) as ARTICLE_SALE stock consumption movements.
     * This drives the estimated stock used in inventory summary.
     *
     * Idempotent: if movements already exist for a SalesProduct (referenceId), it is skipped.
     *
     * POST /api/stock/inventory/apply-sales?date=2026-03-11
     */
    @PostMapping("/apply-sales")
    public ResponseEntity<Map<String, Object>> applySalesForDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        int processed = stockSalesSyncService.applySalesForDate(date);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Sales applied to stock successfully");
        response.put("processedCount", processed);
        response.put("date", date);
        response.put("timestamp", java.time.OffsetDateTime.now());
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
