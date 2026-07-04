package io.storeyes.storeyes_coffee.charges.repositories;

import io.storeyes.storeyes_coffee.charges.entities.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    @Query("SELECT e FROM Employee e WHERE e.store.id = :storeId AND e.name = :name AND " +
           "(:type IS NULL OR e.type = :type) AND " +
           "(:startDate IS NULL OR e.startDate = :startDate)")
    Optional<Employee> findByStoreIdAndNameAndTypeAndStartDate(
            @Param("storeId") Long storeId,
            @Param("name") String name,
            @Param("type") String type,
            @Param("startDate") LocalDate startDate);

    @Query("SELECT e FROM Employee e WHERE e.store.id = :storeId AND " +
           "(:type IS NULL OR e.type = :type) " +
           "ORDER BY e.name ASC")
    List<Employee> findByStoreIdAndType(@Param("storeId") Long storeId, @Param("type") String type);
}
