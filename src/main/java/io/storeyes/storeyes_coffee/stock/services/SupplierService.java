package io.storeyes.storeyes_coffee.stock.services;

import io.storeyes.storeyes_coffee.security.CurrentStoreContext;
import io.storeyes.storeyes_coffee.stock.dto.*;
import io.storeyes.storeyes_coffee.stock.entities.StockProduct;
import io.storeyes.storeyes_coffee.stock.entities.Supplier;
import io.storeyes.storeyes_coffee.stock.entities.SupplierStockProduct;
import io.storeyes.storeyes_coffee.stock.repositories.StockProductRepository;
import io.storeyes.storeyes_coffee.stock.repositories.SupplierRepository;
import io.storeyes.storeyes_coffee.stock.repositories.SupplierStockProductRepository;
import io.storeyes.storeyes_coffee.store.entities.Store;
import io.storeyes.storeyes_coffee.store.repositories.StoreRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierStockProductRepository supplierStockProductRepository;
    private final StockProductRepository stockProductRepository;
    private final StoreRepository storeRepository;

    private Long getStoreId() {
        return CurrentStoreContext.requireCurrentStoreId();
    }

    /**
     * @param includeInactive when true, returns active and inactive suppliers (backoffice). When false, only active
     *                        (filters, mobile pickers).
     */
    public List<SupplierSummaryResponse> listSuppliers(String search, boolean includeInactive) {
        Long storeId = getStoreId();
        List<Supplier> suppliers;
        if (!includeInactive) {
            if (search != null && !search.isBlank()) {
                suppliers = supplierRepository.findByStoreIdAndIsActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc(
                        storeId, search.trim());
            } else {
                suppliers = supplierRepository.findByStoreIdAndIsActiveTrueOrderByNameAsc(storeId);
            }
        } else {
            if (search != null && !search.isBlank()) {
                suppliers = supplierRepository.findByStoreIdAndNameContainingIgnoreCaseOrderByIsActiveDescNameAsc(
                        storeId, search.trim());
            } else {
                suppliers = supplierRepository.findByStoreIdOrderByIsActiveDescNameAsc(storeId);
            }
        }
        if (suppliers.isEmpty()) {
            return List.of();
        }
        List<Long> ids = suppliers.stream().map(Supplier::getId).collect(Collectors.toList());
        Map<Long, Long> countBySupplier = new HashMap<>();
        for (Object[] row : supplierStockProductRepository.countLinksBySupplierIdIn(ids)) {
            countBySupplier.put((Long) row[0], (Long) row[1]);
        }
        return suppliers.stream()
                .map(s -> SupplierSummaryResponse.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .code(s.getCode())
                        .email(s.getEmail())
                        .phone(s.getPhone())
                        .isActive(s.getIsActive())
                        .linkedProductCount(countBySupplier.getOrDefault(s.getId(), 0L))
                        .build())
                .collect(Collectors.toList());
    }

    public SupplierDetailResponse getSupplierById(Long id) {
        Long storeId = getStoreId();
        Supplier supplier = supplierRepository.findByIdAndStore_Id(id, storeId)
                .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + id));
        List<SupplierStockProduct> links =
                supplierStockProductRepository.findBySupplier_IdOrderByStockProduct_NameAsc(id);
        List<SupplierStockLinkResponse> stockRows = links.stream()
                .map(l -> SupplierStockLinkResponse.builder()
                        .stockProductId(l.getStockProduct().getId())
                        .stockProductName(l.getStockProduct().getName())
                        .supplierSku(l.getSupplierSku())
                        .isPreferred(l.getIsPreferred())
                        .build())
                .collect(Collectors.toList());
        return SupplierDetailResponse.builder()
                .id(supplier.getId())
                .name(supplier.getName())
                .code(supplier.getCode())
                .email(supplier.getEmail())
                .phone(supplier.getPhone())
                .notes(supplier.getNotes())
                .isActive(supplier.getIsActive())
                .linkedProductCount(stockRows.size())
                .stockProducts(stockRows)
                .createdAt(supplier.getCreatedAt())
                .updatedAt(supplier.getUpdatedAt())
                .build();
    }

    @Transactional
    public SupplierDetailResponse createSupplier(CreateSupplierRequest request) {
        Long storeId = getStoreId();
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new RuntimeException("Store not found with id: " + storeId));
        Supplier supplier = Supplier.builder()
                .store(store)
                .name(request.getName().trim())
                .code(trimOrNull(request.getCode()))
                .email(trimOrNull(request.getEmail()))
                .phone(trimOrNull(request.getPhone()))
                .notes(trimOrNull(request.getNotes()))
                .isActive(true)
                .build();
        Supplier saved = supplierRepository.save(supplier);
        return getSupplierById(saved.getId());
    }

    @Transactional
    public SupplierDetailResponse updateSupplier(Long id, UpdateSupplierRequest request) {
        Long storeId = getStoreId();
        Supplier supplier = supplierRepository.findByIdAndStore_Id(id, storeId)
                .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + id));
        if (request.getName() != null) {
            supplier.setName(request.getName().trim());
        }
        if (request.getCode() != null) {
            supplier.setCode(trimOrNull(request.getCode()));
        }
        if (request.getEmail() != null) {
            supplier.setEmail(trimOrNull(request.getEmail()));
        }
        if (request.getPhone() != null) {
            supplier.setPhone(trimOrNull(request.getPhone()));
        }
        if (request.getNotes() != null) {
            supplier.setNotes(trimOrNull(request.getNotes()));
        }
        if (request.getIsActive() != null) {
            supplier.setIsActive(request.getIsActive());
        }
        supplierRepository.save(supplier);
        if (!Boolean.TRUE.equals(supplier.getIsActive())) {
            return SupplierDetailResponse.builder()
                    .id(supplier.getId())
                    .name(supplier.getName())
                    .code(supplier.getCode())
                    .email(supplier.getEmail())
                    .phone(supplier.getPhone())
                    .notes(supplier.getNotes())
                    .isActive(supplier.getIsActive())
                    .linkedProductCount(supplierStockProductRepository.countBySupplier_Id(id))
                    .stockProducts(List.of())
                    .createdAt(supplier.getCreatedAt())
                    .updatedAt(supplier.getUpdatedAt())
                    .build();
        }
        return getSupplierById(id);
    }

    /**
     * Permanently delete supplier and all product links (DB cascade also removes link rows).
     */
    @Transactional
    public void deleteSupplierPermanently(Long id) {
        Long storeId = getStoreId();
        Supplier supplier = supplierRepository.findByIdAndStore_Id(id, storeId)
                .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + id));
        supplierStockProductRepository.deleteBySupplier_Id(id);
        supplierRepository.delete(supplier);
    }

    @Transactional
    public SupplierDetailResponse setSupplierStockProducts(Long supplierId, SetSupplierStockProductsRequest request) {
        Long storeId = getStoreId();
        Supplier supplier = supplierRepository.findByIdAndStore_Id(supplierId, storeId)
                .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + supplierId));
        List<SupplierStockProductItemRequest> items = request.getItems();
        if (items == null) {
            items = List.of();
        }
        Set<Long> seenProductIds = new HashSet<>();
        for (SupplierStockProductItemRequest item : items) {
            if (!seenProductIds.add(item.getStockProductId())) {
                throw new RuntimeException("Duplicate stock product in link list");
            }
        }
        for (SupplierStockProductItemRequest item : items) {
            StockProduct product = stockProductRepository.findById(item.getStockProductId())
                    .orElseThrow(() -> new RuntimeException("Stock product not found with id: " + item.getStockProductId()));
            if (!product.getStore().getId().equals(storeId)) {
                throw new RuntimeException("Stock product does not belong to your store");
            }
        }
        supplierStockProductRepository.deleteBySupplier_Id(supplierId);
        supplierStockProductRepository.flush();
        for (SupplierStockProductItemRequest item : items) {
            StockProduct product = stockProductRepository.findById(item.getStockProductId()).orElseThrow();
            SupplierStockProduct link = SupplierStockProduct.builder()
                    .supplier(supplier)
                    .stockProduct(product)
                    .supplierSku(trimOrNull(item.getSupplierSku()))
                    .isPreferred(Boolean.TRUE.equals(item.getIsPreferred()))
                    .build();
            supplierStockProductRepository.save(link);
        }
        supplierStockProductRepository.flush();
        Set<Long> preferredProductIds = items.stream()
                .filter(i -> Boolean.TRUE.equals(i.getIsPreferred()))
                .map(SupplierStockProductItemRequest::getStockProductId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (Long productId : preferredProductIds) {
            supplierStockProductRepository.clearPreferredForStockProduct(productId);
            supplierStockProductRepository.markPreferredForSupplierAndProduct(productId, supplierId);
        }
        return getSupplierById(supplierId);
    }

    public List<SupplierForProductResponse> listSuppliersForStockProduct(Long stockProductId) {
        Long storeId = getStoreId();
        StockProduct product = stockProductRepository.findById(stockProductId)
                .orElseThrow(() -> new RuntimeException("Stock product not found with id: " + stockProductId));
        if (!product.getStore().getId().equals(storeId)) {
            throw new RuntimeException("Stock product not found with id: " + stockProductId);
        }
        return supplierStockProductRepository.findByStoreIdAndStockProductId(storeId, stockProductId).stream()
                .map(l -> SupplierForProductResponse.builder()
                        .supplierId(l.getSupplier().getId())
                        .supplierName(l.getSupplier().getName())
                        .supplierSku(l.getSupplierSku())
                        .isPreferred(l.getIsPreferred())
                        .build())
                .collect(Collectors.toList());
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
