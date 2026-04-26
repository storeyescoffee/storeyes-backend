package io.storeyes.storeyes_coffee.rolemapping.repositories;

import io.storeyes.storeyes_coffee.rolemapping.entities.RoleMapping;
import io.storeyes.storeyes_coffee.rolemapping.entities.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleMappingRepository extends JpaRepository<RoleMapping, Long> {

    /**
     * Find role mapping by user ID and role (e.g. to get the store owned by a user).
     */
    Optional<RoleMapping> findByUser_IdAndRole(String userId, Role role);

    /**
     * Find role mapping by user ID and role name.
     */
    Optional<RoleMapping> findByUser_IdAndRole_Name(String userId, String roleName);

    /**
     * Find any role mapping for the user (any role).
     */
    Optional<RoleMapping> findFirstByUser_Id(String userId);
}
