package io.storeyes.storeyes_coffee.store.services;

import io.storeyes.storeyes_coffee.store.entities.DemoStoreMapping;
import io.storeyes.storeyes_coffee.store.entities.Store;
import io.storeyes.storeyes_coffee.store.repositories.DemoStoreMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Resolves which store's data to read when the context store is configured as a demo.
 */
@Component
@RequiredArgsConstructor
public class DemoStoreDataSourceResolver {

    private final DemoStoreMappingRepository demoStoreMappingRepository;

    public Long resolveAlertsDataStoreId(Long contextStoreId) {
        return demoStoreMappingRepository.findByDemoStore_Id(contextStoreId)
                .map(DemoStoreMapping::getAlertsSourceStore)
                .filter(s -> s != null)
                .map(s -> s.getId())
                .orElse(contextStoreId);
    }

    /**
     * KPI facts are read from {@link KpiDataContext#dataStoreId()}.
     * Revenue and item quantities in the report are multiplied by {@link KpiDataContext#revenueQuantityMultiplier()}
     * where multiplier = 1 + augmentation (e.g. augmentation 0.1 → 10% higher revenue and quantities).
     */
    public KpiDataContext resolveKpiContext(Long contextStoreId) {
        return demoStoreMappingRepository.findByDemoStore_Id(contextStoreId)
                .map(m -> {
                    Long dataStoreId = m.getKpiSourceStore() != null ? m.getKpiSourceStore().getId() : contextStoreId;
                    BigDecimal aug = m.getKpiAugmentationPercent();
                    double mult = aug != null ? 1.0 + aug.doubleValue() : 1.0;
                    return new KpiDataContext(dataStoreId, mult);
                })
                .orElse(new KpiDataContext(contextStoreId, 1.0));
    }

    /**
     * When there is no demo row for this store, or {@code stockSourceStore} is null, returns {@code contextStoreId}.
     */
    public Long resolveStockDataStoreId(Long contextStoreId) {
        Optional<DemoStoreMapping> row = demoStoreMappingRepository.findByDemoStore_Id(contextStoreId);
        if (row.isEmpty()) {
            return contextStoreId;
        }
        Store source = row.get().getStockSourceStore();
        return source != null ? source.getId() : contextStoreId;
    }

    public Long resolveChargesDataStoreId(Long contextStoreId) {
        return demoStoreMappingRepository.findByDemoStore_Id(contextStoreId)
                .map(DemoStoreMapping::getChargesSourceStore)
                .filter(s -> s != null)
                .map(s -> s.getId())
                .orElse(contextStoreId);
    }

    /**
     * Access-control events (and related data) are read from this store when the context store is a demo
     * with {@link DemoStoreMapping#getAccessSourceStore()} set; otherwise {@code contextStoreId}.
     */
    public Long resolveAccessDataStoreId(Long contextStoreId) {
        return demoStoreMappingRepository.findByDemoStore_Id(contextStoreId)
                .map(DemoStoreMapping::getAccessSourceStore)
                .filter(s -> s != null)
                .map(Store::getId)
                .orElse(contextStoreId);
    }

    public record KpiDataContext(Long dataStoreId, double revenueQuantityMultiplier) {}
}
