package io.storeyes.storeyes_coffee.stock.controllers;

import io.storeyes.storeyes_coffee.stock.dto.StockSummaryResponse;
import io.storeyes.storeyes_coffee.stock.services.StockMovementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockSummaryController {

    private final StockMovementService stockMovementService;

    /**
     * Stock hub cards in one response: total value, inventory line diff count, to-buy count.
     * Same semantics as GET /api/stock/inventory and GET /api/stock/inventory/to-buy aggregates.
     * GET /api/stock/summary
     */
    @GetMapping("/summary")
    public ResponseEntity<StockSummaryResponse> getSummary() {
        return ResponseEntity.ok(stockMovementService.getStockHubSummary());
    }
}
