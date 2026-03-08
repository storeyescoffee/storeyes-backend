package io.storeyes.storeyes_coffee.store.services;

import io.storeyes.storeyes_coffee.store.dto.CreateStoreRequest;
import io.storeyes.storeyes_coffee.store.dto.PaginatedResponse;
import io.storeyes.storeyes_coffee.store.dto.StoreDTO;
import io.storeyes.storeyes_coffee.store.dto.StoreFilterDto;
import io.storeyes.storeyes_coffee.store.entities.Store;
import io.storeyes.storeyes_coffee.store.entities.StoreStatus;
import io.storeyes.storeyes_coffee.store.mappers.StoreMapper;
import io.storeyes.storeyes_coffee.store.repositories.StoreRepository;
import io.storeyes.storeyes_coffee.store.specifications.StoreSpecification;
import io.storeyes.storeyes_coffee.rolemapping.repositories.RoleMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StoreService {
    
    private final StoreRepository storeRepository;
    private final StoreMapper storeMapper;
    private final RoleMappingRepository roleMappingRepository;
    
    /**
     * Create a new store
     */
    public StoreDTO createStore(CreateStoreRequest request) {
        // Check if store with the same code already exists
        if (storeRepository.findByCode(request.getCode()).isPresent()) {
            throw new RuntimeException("Store with code " + request.getCode() + " already exists");
        }
        
        Store store = Store.builder()
                .code(request.getCode())
                .name(request.getName())
                .address(request.getAddress())
                .coordinates(request.getCoordinates())
                .city(request.getCity())
                .type(request.getType())
                .status(request.getStatus() != null ? request.getStatus() : StoreStatus.NEW)
                .build();
        
        Store savedStore = storeRepository.save(store);
        return storeMapper.toDTO(savedStore);
    }
    
    /**
     * Get store by code
     */
    public StoreDTO getStoreByCode(String code) {
        Store store = storeRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Store not found with code: " + code));
        return storeMapper.toDTO(store);
    }
    
    /**
     * Get store by ID
     */
    public StoreDTO getStoreById(Long id) {
        Store store = storeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Store not found with id: " + id));
        return storeMapper.toDTO(store);
    }

    /**
     * Get store entity by ID. Used when store context provides the ID.
     */
    public Store getStoreEntityById(Long id) {
        return storeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Store not found with id: " + id));
    }
    
    /**
     * Get store entity by user ID via RoleMapping (OWNER role).
     */
    public Store getStoreEntityByOwnerId(String userId) {
        return roleMappingRepository.findByUser_IdAndRole_Name(userId, "OWNER")
                .map(rm -> rm.getStore())
                .orElseThrow(() -> new RuntimeException("Store not found for user with id: " + userId));
    }

    /**
     * Get store by owner ID (Keycloak user ID) - resolves store via RoleMapping (OWNER role).
     */
    public StoreDTO getStoreByOwnerId(String ownerId) {
        return storeMapper.toDTO(getStoreEntityByOwnerId(ownerId));
    }
    
    /**
     * Get paginated stores with filtering using JPA Specifications
     */
    public PaginatedResponse<StoreDTO> getStores(StoreFilterDto filter, int page, int size, String sortBy, String sortDir) {
        // Build specification from filter
        Specification<Store> spec = StoreSpecification.buildSpecification(filter);
        
        // Create sort object
        Sort sort = sortDir.equalsIgnoreCase("desc") 
            ? Sort.by(sortBy).descending() 
            : Sort.by(sortBy).ascending();
        
        // Create pageable
        Pageable pageable = PageRequest.of(page, size, sort);
        
        // Execute query with specification
        Page<Store> storePage = storeRepository.findAll(spec, pageable);
        
        // Convert to DTOs
        List<StoreDTO> storeDTOs = storeMapper.toDTOList(storePage.getContent());
        
        // Build paginated response
        return PaginatedResponse.<StoreDTO>builder()
                .content(storeDTOs)
                .page(storePage.getNumber())
                .size(storePage.getSize())
                .totalElements(storePage.getTotalElements())
                .totalPages(storePage.getTotalPages())
                .first(storePage.isFirst())
                .last(storePage.isLast())
                .build();
    }
}



