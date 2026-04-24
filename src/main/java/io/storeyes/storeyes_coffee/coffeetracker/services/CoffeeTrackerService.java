package io.storeyes.storeyes_coffee.coffeetracker.services;

import io.storeyes.storeyes_coffee.coffeetracker.entities.CoffeeTracker;
import io.storeyes.storeyes_coffee.coffeetracker.entities.TrackerStatus;
import io.storeyes.storeyes_coffee.coffeetracker.repositories.CoffeeTrackerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CoffeeTrackerService {

    private final CoffeeTrackerRepository coffeeTrackerRepository;

    public List<CoffeeTracker> findCompletedByStoreAndDate(Long storeId, LocalDate date) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        return coffeeTrackerRepository.findByStoreIdAndStatusAndTimestampOnLocalDate(
                storeId, TrackerStatus.COMPLETED, dayStart, dayEnd);
    }
}
