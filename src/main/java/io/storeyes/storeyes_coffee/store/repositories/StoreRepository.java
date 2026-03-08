package io.storeyes.storeyes_coffee.store.repositories;

import io.storeyes.storeyes_coffee.store.entities.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StoreRepository extends JpaRepository<Store, Long>, JpaSpecificationExecutor<Store> {
    
    /**
     * Find store by code
     */
    Optional<Store> findByCode(String code);
}



