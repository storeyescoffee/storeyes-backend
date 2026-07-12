package io.storeyes.storeyes_coffee.store.services;

import io.storeyes.storeyes_coffee.charges.entities.VariableChargeMainCategory;
import io.storeyes.storeyes_coffee.charges.entities.VariableChargeSubCategory;
import io.storeyes.storeyes_coffee.charges.repositories.VariableChargeMainCategoryRepository;
import io.storeyes.storeyes_coffee.charges.repositories.VariableChargeSubCategoryRepository;
import io.storeyes.storeyes_coffee.store.dto.CreateStoreRequest;
import io.storeyes.storeyes_coffee.store.dto.PaginatedResponse;
import io.storeyes.storeyes_coffee.store.dto.StoreDTO;
import io.storeyes.storeyes_coffee.store.dto.StoreFilterDto;
import io.storeyes.storeyes_coffee.store.entities.Store;
import io.storeyes.storeyes_coffee.store.entities.StoreStatus;
import io.storeyes.storeyes_coffee.store.mappers.StoreMapper;
import io.storeyes.storeyes_coffee.store.repositories.StoreRepository;
import io.storeyes.storeyes_coffee.store.specifications.StoreSpecification;
import io.storeyes.storeyes_coffee.rolemapping.entities.RoleMapping;
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
    private final VariableChargeMainCategoryRepository variableChargeMainCategoryRepository;
    private final VariableChargeSubCategoryRepository variableChargeSubCategoryRepository;

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
        ensureDefaultStockCategories(savedStore);
        return storeMapper.toDTO(savedStore);
    }

    /**
     * Ensures the store has the "Stock" main category and "Raw materials" sub-category that the
     * mobile inventory feature relies on (see StockProductRepository.findRawMaterialProductsByStoreIdOrderByNameAsc).
     * Idempotent — safe to call on a store that already has them.
     */
    private void ensureDefaultStockCategories(Store store) {
        VariableChargeMainCategory stockCategory = variableChargeMainCategoryRepository
                .findByStore_IdAndCodeIgnoreCase(store.getId(), "stock")
                .orElseGet(() -> variableChargeMainCategoryRepository.save(
                        VariableChargeMainCategory.builder()
                                .store(store)
                                .name("Stock")
                                .code("stock")
                                .sortOrder(1)
                                .build()));

        variableChargeSubCategoryRepository
                .findByMainCategory_IdAndCodeIgnoreCase(stockCategory.getId(), "raw_materials")
                .orElseGet(() -> variableChargeSubCategoryRepository.save(
                        VariableChargeSubCategory.builder()
                                .mainCategory(stockCategory)
                                .parentSubCategory(null)
                                .name("Raw materials")
                                .code("raw_materials")
                                .sortOrder(1)
                                .active(true)
                                .build()));
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
     * Store for the given Keycloak user — first {@link RoleMapping} for that user (any role).
     */
    public Store getStoreEntityForUser(String userId) {
        return roleMappingRepository.findFirstByUser_IdOrderByStore_IdAsc(userId)
                .map(RoleMapping::getStore)
                .orElseThrow(() -> new RuntimeException("No store assigned for user with id: " + userId));
    }

    /**
     * Resolves store from role_mappings without using {@link io.storeyes.storeyes_coffee.security.CurrentStoreContext}.
     * Prefer {@link io.storeyes.storeyes_coffee.security.CurrentStoreContext#requireCurrentStoreId()} in HTTP request code
     * (set by {@link io.storeyes.storeyes_coffee.security.StoreContextInterceptor}); use this for jobs/tests with no request.
     */
    public Long getStoreIdForUser(String userId) {
        return getStoreEntityForUser(userId).getId();
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



