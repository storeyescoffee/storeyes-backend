package io.storeyes.storeyes_coffee.coffeetracker.repositories;

import io.storeyes.storeyes_coffee.coffeetracker.entities.CoffeeTracker;
import io.storeyes.storeyes_coffee.coffeetracker.entities.TrackerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CoffeeTrackerRepository extends JpaRepository<CoffeeTracker, Long> {

    /**
     * Completed trackers for a store whose {@code timestamp} falls on the given local calendar day.
     * Equivalent to {@code DATE(timestamp) = :date} in PostgreSQL for {@code timestamp without time zone}.
     */
    @Query("SELECT c FROM CoffeeTracker c WHERE c.store.id = :storeId AND c.status = :status "
            + "AND c.timestamp >= :dayStart AND c.timestamp < :dayEnd ORDER BY c.timestamp ASC")
    List<CoffeeTracker> findByStoreIdAndStatusAndTimestampOnLocalDate(
            @Param("storeId") Long storeId,
            @Param("status") TrackerStatus status,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd);
}
