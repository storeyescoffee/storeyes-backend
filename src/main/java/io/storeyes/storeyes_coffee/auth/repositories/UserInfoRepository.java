package io.storeyes.storeyes_coffee.auth.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.storeyes.storeyes_coffee.auth.entities.UserInfo;

import java.util.Optional;

@Repository
public interface UserInfoRepository extends JpaRepository<UserInfo, String> {
    
    /**
     * Find user info by email
     */
    Optional<UserInfo> findByEmail(String email);

    Optional<UserInfo> findByEmailIgnoreCase(String email);
}
