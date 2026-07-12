package io.storeyes.storeyes_coffee.firebase.repositories;

import io.storeyes.storeyes_coffee.firebase.entities.FirebaseToken2;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FirebaseToken2Repository extends JpaRepository<FirebaseToken2, Long> {

    Optional<FirebaseToken2> findByMobileId(String mobileId);

    @Query("""
            SELECT DISTINCT ft.firebaseToken
            FROM FirebaseToken2 ft
            WHERE ft.user.id = :userId
              AND ft.isLoggedIn = true
            """)
    List<String> findActiveTokensByUserId(@Param("userId") String userId);

    @Query("""
            SELECT DISTINCT ft.firebaseToken
            FROM FirebaseToken2 ft
            JOIN RoleMapping rm ON rm.user.id = ft.user.id
            WHERE rm.store.id = :storeId
              AND ft.isLoggedIn = true
              AND ft.user IS NOT NULL
            """)
    List<String> findActiveTokensByStoreId(@Param("storeId") Long storeId);

    @Query("""
            SELECT DISTINCT ft.firebaseToken
            FROM FirebaseToken2 ft
            JOIN RoleMapping rm ON rm.user.id = ft.user.id
            WHERE rm.store.id = :storeId
              AND ft.isLoggedIn = true
              AND ft.user IS NOT NULL
              AND rm.role.name IN :roleNames
            """)
    List<String> findActiveTokensByStoreIdAndRoles(
            @Param("storeId") Long storeId,
            @Param("roleNames") List<String> roleNames);

    /**
     * Active tokens of the users holding one of {@code roleNames} in the given store, minus one user.
     * Role names are compared upper-cased, so the caller need not know how {@code roles.name} is cased.
     *
     * @param excludedUserId user to leave out, or null to exclude nobody
     */
    @Query("""
            SELECT DISTINCT ft.firebaseToken
            FROM FirebaseToken2 ft
            JOIN RoleMapping rm ON rm.user.id = ft.user.id
            WHERE rm.store.id = :storeId
              AND ft.isLoggedIn = true
              AND ft.user IS NOT NULL
              AND UPPER(rm.role.name) IN :roleNames
              AND (:excludedUserId IS NULL OR ft.user.id <> :excludedUserId)
            """)
    List<String> findActiveTokensByStoreIdAndRolesExcludingUser(
            @Param("storeId") Long storeId,
            @Param("roleNames") List<String> roleNames,
            @Param("excludedUserId") String excludedUserId);
}
