package io.storeyes.storeyes_coffee.store.repositories;

import io.storeyes.storeyes_coffee.store.entities.DemoStoreMapping;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DemoStoreMappingRepository extends JpaRepository<DemoStoreMapping, Long> {

    @EntityGraph(attributePaths = {"alertsSourceStore", "kpiSourceStore", "stockSourceStore", "chargesSourceStore"})
    Optional<DemoStoreMapping> findByDemoStore_Id(Long demoStoreId);
}
