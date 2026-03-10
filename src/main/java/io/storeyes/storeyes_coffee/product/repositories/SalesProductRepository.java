package io.storeyes.storeyes_coffee.product.repositories;

import io.storeyes.storeyes_coffee.product.entities.SalesProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SalesProductRepository extends JpaRepository<SalesProduct, Long> {

    @Query("SELECT sp FROM SalesProduct sp JOIN FETCH sp.product p WHERE p.store.id = :storeId AND sp.date = :date ORDER BY sp.id")
    List<SalesProduct> findByStoreIdAndDate(@Param("storeId") Long storeId, @Param("date") LocalDate date);
}
