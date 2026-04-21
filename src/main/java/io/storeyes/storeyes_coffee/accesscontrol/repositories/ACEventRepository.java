package io.storeyes.adminpanel.accesscontrol.repositories;

import io.storeyes.adminpanel.accesscontrol.entities.ACEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ACEventRepository extends JpaRepository<ACEvent, Long> {

    /**
     * Events for a store on the given calendar day, staff loaded in the same query (no N+1).
     */
    @Query("""
            SELECT e FROM ACEvent e
            JOIN FETCH e.accessControlStaff
            WHERE e.store.id = :storeId
            AND e.eventTimestamp >= :startInclusive
            AND e.eventTimestamp < :endExclusive
            ORDER BY e.eventTimestamp ASC
            """)
    List<ACEvent> findByStoreIdAndEventTimestampDay(
            @Param("storeId") Long storeId,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive);
}
