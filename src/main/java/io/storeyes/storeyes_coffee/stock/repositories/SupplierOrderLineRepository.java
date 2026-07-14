package io.storeyes.storeyes_coffee.stock.repositories;

import io.storeyes.storeyes_coffee.stock.entities.SupplierOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierOrderLineRepository extends JpaRepository<SupplierOrderLine, Long> {

    void deleteByStockProduct_Id(Long stockProductId);
}
