package io.storeyes.storeyes_coffee.coffeetracker.services;

import io.storeyes.storeyes_coffee.coffeetracker.dto.CoffeeTrackerEventDTO;
import io.storeyes.storeyes_coffee.coffeetracker.entities.CoffeeTracker;
import io.storeyes.storeyes_coffee.coffeetracker.entities.TrackerStatus;
import io.storeyes.storeyes_coffee.coffeetracker.repositories.CoffeeTrackerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CoffeeTrackerService {

    private final CoffeeTrackerRepository coffeeTrackerRepository;

    /**
     * Fetch COMPLETED state events for the given store and date, ordered by timestamp.
     */
    @Transactional(readOnly = true)
    public List<CoffeeTrackerEventDTO> getCompletedEventsByStoreAndDate(Long storeId, LocalDate date) {
        List<CoffeeTracker> events = coffeeTrackerRepository
                .findByStore_IdAndDateAndStatusOrderByTimestampAsc(storeId, date, TrackerStatus.COMPLETED);
        return events.stream().map(this::toDTO).collect(Collectors.toList());
    }

    private CoffeeTrackerEventDTO toDTO(CoffeeTracker e) {
        return CoffeeTrackerEventDTO.builder()
                .id(e.getId())
                .date(e.getDate())
                .timestamp(e.getTimestamp())
                .quantity(e.getQuantity())
                .status(e.getStatus())
                .previousId(e.getPrevious() != null ? e.getPrevious().getId() : null)
                .build();
    }
}
