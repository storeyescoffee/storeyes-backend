package io.storeyes.storeyes_coffee.firebase.repositories;

import io.storeyes.storeyes_coffee.firebase.entities.FirebaseToken2;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FirebaseToken2Repository extends JpaRepository<FirebaseToken2, Long> {

    /**
     * Find token by mobileId for login/logout action handling
     */
    Optional<FirebaseToken2> findByMobileId(String mobileId);
}
