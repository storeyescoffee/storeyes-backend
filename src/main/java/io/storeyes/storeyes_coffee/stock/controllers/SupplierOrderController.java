package io.storeyes.storeyes_coffee.stock.controllers;

import io.storeyes.storeyes_coffee.stock.dto.*;
import io.storeyes.storeyes_coffee.stock.services.SupplierOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock/supplier-orders")
@RequiredArgsConstructor
public class SupplierOrderController {

    private final SupplierOrderService supplierOrderService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        List<SupplierOrderSummaryResponse> data = supplierOrderService.listSummaries();
        Map<String, Object> response = new HashMap<>();
        response.put("data", data);
        response.put("message", "Supplier orders retrieved successfully");
        response.put("timestamp", java.time.OffsetDateTime.now());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long id) {
        try {
            SupplierOrderDetailResponse data = supplierOrderService.getById(id);
            Map<String, Object> response = new HashMap<>();
            response.put("data", data);
            response.put("message", "Supplier order retrieved successfully");
            response.put("timestamp", java.time.OffsetDateTime.now());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody CreateSupplierOrderRequest request) {
        try {
            SupplierOrderDetailResponse data = supplierOrderService.create(request);
            Map<String, Object> response = new HashMap<>();
            response.put("data", data);
            response.put("message", "Supplier order created successfully");
            response.put("timestamp", java.time.OffsetDateTime.now());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSupplierOrderRequest request) {
        try {
            SupplierOrderDetailResponse data = supplierOrderService.update(id, request);
            Map<String, Object> response = new HashMap<>();
            response.put("data", data);
            response.put("message", "Supplier order updated successfully");
            response.put("timestamp", java.time.OffsetDateTime.now());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            supplierOrderService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @PostMapping("/{id}/convert")
    public ResponseEntity<Map<String, Object>> convert(
            @PathVariable Long id,
            @RequestBody(required = false) ConvertSupplierOrderRequest request) {
        try {
            SupplierOrderDetailResponse data = supplierOrderService.convertToVariableCharges(id, request);
            Map<String, Object> response = new HashMap<>();
            response.put("data", data);
            response.put("message", "Supplier order converted to variable charges successfully");
            response.put("timestamp", java.time.OffsetDateTime.now());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (IllegalStateException e) {
            return badRequest(e.getMessage());
        }
    }

    private static ResponseEntity<Map<String, Object>> badRequest(String message) {
        Map<String, Object> err = new HashMap<>();
        err.put("error", "Bad Request");
        err.put("message", message != null ? message : "Invalid request");
        err.put("timestamp", java.time.OffsetDateTime.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
    }
}
