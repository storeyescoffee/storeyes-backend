package io.storeyes.storeyes_coffee.auth.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.storeyes.storeyes_coffee.auth.entities.UserPreference;
import io.storeyes.storeyes_coffee.auth.entities.UserPreferenceId;

import java.util.Optional;

@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreference, UserPreferenceId> {

    Optional<UserPreference> findByUserIdAndPreferenceKey(String userId, String preferenceKey);
}
