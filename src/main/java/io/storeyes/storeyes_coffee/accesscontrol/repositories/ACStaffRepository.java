package io.storeyes.adminpanel.accesscontrol.repositories;

import io.storeyes.adminpanel.accesscontrol.entities.ACStaff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ACStaffRepository extends JpaRepository<ACStaff, Long> {

    Optional<ACStaff> findByStore_IdAndCode(Long storeId, String code);
}
