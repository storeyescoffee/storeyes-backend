package io.storeyes.storeyes_coffee.sales.services;

import io.storeyes.storeyes_coffee.sales.cron.SalesProcessor;
import io.storeyes.storeyes_coffee.store.entities.Store;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalesProcessingService {
    
    private final SalesProcessor salesProcessor;
    
    /**
     * Async method to process sales for a store and date
     * @param store The store entity
     * @param date The date to process
     * @return CompletableFuture that completes when processing is done
     */
    @Async("salesProcessingExecutor")
    public CompletableFuture<Integer> processSalesAsync(Store store, LocalDate date) {
        try {
            log.info("Starting async sales processing for store {} on date {}", store.getId(), date);
            int salesCount = salesProcessor.processSalesForStore(store, date);
            log.info("Completed async sales processing for store {} on date {}. Created {} sales records.", 
                    store.getId(), date, salesCount);
            return CompletableFuture.completedFuture(salesCount);
        } catch (Exception e) {
            log.error("Error processing sales for store {} on date {}", store.getId(), date, e);
            CompletableFuture<Integer> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }
}

