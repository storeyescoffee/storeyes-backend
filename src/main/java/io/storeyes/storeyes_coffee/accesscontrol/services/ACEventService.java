package io.storeyes.storeyes_coffee.accesscontrol.services;

import io.storeyes.storeyes_coffee.accesscontrol.entities.ACEvent;
import io.storeyes.storeyes_coffee.accesscontrol.repositories.ACEventRepository;
import io.storeyes.storeyes_coffee.accesscontrol.dto.ACEventByDateItemDTO;
import io.storeyes.storeyes_coffee.store.services.DemoStoreDataSourceResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ACEventService {

    private final ACEventRepository acEventRepository;
    private final DemoStoreDataSourceResolver demoStoreDataSourceResolver;

    @Transactional(readOnly = true)
    public List<ACEventByDateItemDTO> listByStoreAndDate(Long storeId, LocalDate date) {
        Long dataStoreId = demoStoreDataSourceResolver.resolveAccessDataStoreId(storeId);
        LocalDateTime startInclusive = date.atStartOfDay();
        LocalDateTime endExclusive = date.plusDays(1).atStartOfDay();
        List<ACEvent> events = acEventRepository.findByStoreIdAndEventTimestampDay(
                dataStoreId, startInclusive, endExclusive);
        return events.stream().map(this::toItem).collect(Collectors.toList());
    }

    private ACEventByDateItemDTO toItem(ACEvent e) {
        return ACEventByDateItemDTO.builder()
                .code(e.getAccessControlStaff().getCode())
                .name(e.getAccessControlStaff().getName())
                .time(e.getEventTimestamp())
                .build();
    }
}
