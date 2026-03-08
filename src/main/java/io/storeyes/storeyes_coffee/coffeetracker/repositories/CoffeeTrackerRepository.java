package io.storeyes.storeyes_coffee.coffeetracker.repositories;

import io.storeyes.storeyes_coffee.coffeetracker.entities.CoffeeTracker;
import io.storeyes.storeyes_coffee.coffeetracker.entities.TrackerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CoffeeTrackerRepository extends JpaRepository<CoffeeTracker, Long> {

    /**
     * Find all tracker events for a store on a given date with the specified status,
     * ordered by timestamp ascending.
     */
    List<CoffeeTracker> findByStore_IdAndDateAndStatusOrderByTimestampAsc(
            Long storeId, LocalDate date, TrackerStatus status);
}
