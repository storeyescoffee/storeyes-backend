package io.storeyes.storeyes_coffee.product.controllers;

import io.storeyes.storeyes_coffee.product.dto.SalesByStoreDateDTO;
import io.storeyes.storeyes_coffee.product.entities.SalesProduct;
import io.storeyes.storeyes_coffee.product.repositories.SalesProductRepository;
import io.storeyes.storeyes_coffee.security.CurrentStoreContext;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final SalesProductRepository salesProductRepository;

    /**
     * Get list of sales by store and date.
     * Store is determined by the authenticated user's role mapping.
     * GET /api/products/sales?date=2025-01-15
     *
     * @param date Sale date in ISO format (required)
     * @return List of {id, productCode, productName, quantity, price, totalPrice}
     */
    @GetMapping("/sales")
    public ResponseEntity<List<SalesByStoreDateDTO>> getSalesByStoreAndDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        Long storeId = CurrentStoreContext.getCurrentStoreId();
        if (storeId == null) {
            throw new RuntimeException("Store context not found for current user");
        }

        List<SalesProduct> salesProducts = salesProductRepository.findByStoreIdAndDate(storeId, date);
        List<SalesByStoreDateDTO> dtos = salesProducts.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    private SalesByStoreDateDTO toDTO(SalesProduct sp) {
        return SalesByStoreDateDTO.builder()
                .id(sp.getId())
                .productCode(sp.getProduct() != null ? sp.getProduct().getCode() : null)
                .productName(sp.getProduct() != null ? sp.getProduct().getName() : null)
                .quantity(sp.getQuantity())
                .price(sp.getPrice())
                .totalPrice(sp.getTotalPrice())
                .build();
    }
}
