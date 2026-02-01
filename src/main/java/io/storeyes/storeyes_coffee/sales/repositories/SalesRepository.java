package io.storeyes.storeyes_coffee.sales.repositories;

import io.storeyes.storeyes_coffee.sales.entities.Sales;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SalesRepository extends JpaRepository<Sales, Long> {
}
