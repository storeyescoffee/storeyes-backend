package io.storeyes.storeyes_coffee.stock.services;

import io.storeyes.storeyes_coffee.charges.dto.VariableChargeCreateRequest;
import io.storeyes.storeyes_coffee.charges.entities.VariableChargeMainCategory;
import io.storeyes.storeyes_coffee.charges.repositories.VariableChargeMainCategoryRepository;
import io.storeyes.storeyes_coffee.charges.services.ChargeService;
import io.storeyes.storeyes_coffee.security.KeycloakTokenUtils;
import io.storeyes.storeyes_coffee.stock.dto.*;
import io.storeyes.storeyes_coffee.stock.entities.StockProduct;
import io.storeyes.storeyes_coffee.stock.entities.Supplier;
import io.storeyes.storeyes_coffee.stock.entities.SupplierOrder;
import io.storeyes.storeyes_coffee.stock.entities.SupplierOrderLine;
import io.storeyes.storeyes_coffee.stock.entities.SupplierOrderStatus;
import io.storeyes.storeyes_coffee.stock.repositories.StockProductRepository;
import io.storeyes.storeyes_coffee.stock.repositories.SupplierOrderRepository;
import io.storeyes.storeyes_coffee.stock.repositories.SupplierRepository;
import io.storeyes.storeyes_coffee.store.entities.Store;
import io.storeyes.storeyes_coffee.store.repositories.StoreRepository;
import io.storeyes.storeyes_coffee.store.services.StoreService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierOrderService {

    private static final String STOCK_MAIN_CATEGORY_CODE = "stock";

    private final SupplierOrderRepository supplierOrderRepository;
    private final StoreRepository storeRepository;
    private final StoreService storeService;
    private final SupplierRepository supplierRepository;
    private final StockProductRepository stockProductRepository;
    private final VariableChargeMainCategoryRepository variableChargeMainCategoryRepository;
    private final ChargeService chargeService;

    private Long getStoreId() {
        String userId = KeycloakTokenUtils.getUserId();
        if (userId == null) {
            throw new IllegalStateException("User is not authenticated");
        }
        return storeService.getStoreByOwnerId(userId).getId();
    }

    public List<SupplierOrderSummaryResponse> listSummaries() {
        Long storeId = getStoreId();
        List<Object[]> rows = supplierOrderRepository.listSummaryRows(storeId);
        List<SupplierOrderSummaryResponse> out = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            out.add(mapSummaryRow(row));
        }
        return out;
    }

    private SupplierOrderSummaryResponse mapSummaryRow(Object[] row) {
        Long id = ((Number) row[0]).longValue();
        SupplierOrderStatus status = SupplierOrderStatus.valueOf(String.valueOf(row[1]));
        LocalDate orderDate = toLocalDate(row[2]);
        Long supplierId = row[3] == null ? null : ((Number) row[3]).longValue();
        String supplierName = row[4] != null ? row[4].toString() : null;
        int lineCount = ((Number) row[5]).intValue();
        BigDecimal total = row[6] instanceof BigDecimal b ? b : BigDecimal.valueOf(((Number) row[6]).doubleValue());
        LocalDateTime createdAt = toLocalDateTime(row[7]);
        LocalDateTime updatedAt = toLocalDateTime(row[8]);
        return SupplierOrderSummaryResponse.builder()
                .id(id)
                .status(status)
                .orderDate(orderDate)
                .supplierId(supplierId)
                .supplierName(supplierName)
                .lineCount(lineCount)
                .totalAmount(total.setScale(2, RoundingMode.HALF_UP))
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }

    private static LocalDate toLocalDate(Object v) {
        if (v == null) {
            return LocalDate.now();
        }
        if (v instanceof Date d) {
            return d.toLocalDate();
        }
        if (v instanceof LocalDate ld) {
            return ld;
        }
        return LocalDate.parse(v.toString());
    }

    private static LocalDateTime toLocalDateTime(Object v) {
        if (v instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (v instanceof LocalDateTime ldt) {
            return ldt;
        }
        return LocalDateTime.parse(v.toString());
    }

    public SupplierOrderDetailResponse getById(Long id) {
        Long storeId = getStoreId();
        SupplierOrder order = supplierOrderRepository.findFetchedByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier order not found"));
        return toDetailResponse(order);
    }

    @Transactional
    public SupplierOrderDetailResponse create(CreateSupplierOrderRequest request) {
        Long storeId = getStoreId();
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalStateException("Store not found"));

        Supplier supplier = null;
        String nameSnapshot = request.getSupplierNameSnapshot() != null
                ? request.getSupplierNameSnapshot().trim()
                : null;
        if (request.getSupplierId() != null) {
            supplier = supplierRepository.findByIdAndStore_Id(request.getSupplierId(), storeId)
                    .orElseThrow(() -> new IllegalArgumentException("Supplier not found"));
            if (nameSnapshot == null || nameSnapshot.isBlank()) {
                nameSnapshot = supplier.getName();
            }
        }

        LocalDate orderDate = request.getOrderDate() != null ? request.getOrderDate() : LocalDate.now();
        SupplierOrder order = SupplierOrder.builder()
                .store(store)
                .supplier(supplier)
                .supplierNameSnapshot(nameSnapshot)
                .messageText(request.getMessageText() != null ? request.getMessageText() : "")
                .status(SupplierOrderStatus.DRAFT)
                .orderDate(orderDate)
                .build();

        List<SupplierOrderLine> lines = buildLines(order, storeId, request.getLines());
        order.setLines(lines);
        SupplierOrder saved = supplierOrderRepository.save(order);
        return toDetailResponse(supplierOrderRepository.findFetchedByIdAndStoreId(saved.getId(), storeId).orElse(saved));
    }

    @Transactional
    public SupplierOrderDetailResponse update(Long id, UpdateSupplierOrderRequest request) {
        Long storeId = getStoreId();
        SupplierOrder order = supplierOrderRepository.findFetchedByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier order not found"));
        if (order.getStatus() == SupplierOrderStatus.CONVERTED) {
            throw new IllegalArgumentException("Cannot update a converted supplier order");
        }
        if (request.getStatus() != null && request.getStatus() == SupplierOrderStatus.CONVERTED) {
            throw new IllegalArgumentException("Invalid status");
        }

        if (request.getOrderDate() != null) {
            order.setOrderDate(request.getOrderDate());
        }
        if (request.getMessageText() != null) {
            order.setMessageText(request.getMessageText());
        }

        if (request.getSupplierId() != null) {
            Supplier supplier = supplierRepository.findByIdAndStore_Id(request.getSupplierId(), storeId)
                    .orElseThrow(() -> new IllegalArgumentException("Supplier not found"));
            order.setSupplier(supplier);
            if (request.getSupplierNameSnapshot() != null && !request.getSupplierNameSnapshot().isBlank()) {
                order.setSupplierNameSnapshot(request.getSupplierNameSnapshot().trim());
            } else {
                order.setSupplierNameSnapshot(supplier.getName());
            }
        } else {
            order.setSupplier(null);
            if (request.getSupplierNameSnapshot() != null) {
                order.setSupplierNameSnapshot(request.getSupplierNameSnapshot().trim());
            }
        }

        if (request.getStatus() != null) {
            if (request.getStatus() == SupplierOrderStatus.SENT && order.getStatus() == SupplierOrderStatus.DRAFT) {
                order.setStatus(SupplierOrderStatus.SENT);
            } else if (request.getStatus() == SupplierOrderStatus.DRAFT && order.getStatus() == SupplierOrderStatus.SENT) {
                order.setStatus(SupplierOrderStatus.DRAFT);
            } else if (request.getStatus() != order.getStatus()) {
                throw new IllegalArgumentException("Invalid status transition");
            }
        }

        order.getLines().clear();
        order.getLines().addAll(buildLines(order, storeId, request.getLines()));
        SupplierOrder saved = supplierOrderRepository.save(order);
        return toDetailResponse(supplierOrderRepository.findFetchedByIdAndStoreId(saved.getId(), storeId).orElse(saved));
    }

    @Transactional
    public void delete(Long id) {
        Long storeId = getStoreId();
        SupplierOrder order = supplierOrderRepository.findByIdAndStore_Id(id, storeId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier order not found"));
        if (order.getStatus() == SupplierOrderStatus.CONVERTED) {
            throw new IllegalArgumentException("Cannot delete a converted supplier order");
        }
        supplierOrderRepository.delete(order);
    }

    @Transactional
    public SupplierOrderDetailResponse convertToVariableCharges(Long id, ConvertSupplierOrderRequest request) {
        Long storeId = getStoreId();
        SupplierOrder order = supplierOrderRepository.findFetchedByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier order not found"));
        if (order.getStatus() == SupplierOrderStatus.CONVERTED) {
            throw new IllegalArgumentException("Order was already converted to expenses");
        }
        if (order.getLines() == null || order.getLines().isEmpty()) {
            throw new IllegalArgumentException("Order has no lines to convert");
        }

        VariableChargeMainCategory stockMain = variableChargeMainCategoryRepository
                .findByStore_IdAndCodeIgnoreCase(storeId, STOCK_MAIN_CATEGORY_CODE)
                .orElseThrow(() -> new IllegalStateException(
                        "Stock variable charge main category (code=stock) not found for this store"));

        LocalDate chargeDate = request != null && request.getChargeDate() != null
                ? request.getChargeDate()
                : LocalDate.now();
        Boolean updateCatalog = request != null && Boolean.TRUE.equals(request.getUpdateStockProductUnitPrice());

        String supplierLabel = resolveSupplierLabel(order);
        List<SupplierOrderLine> sorted = order.getLines().stream()
                .sorted(Comparator.comparingInt(l -> l.getSortIndex() != null ? l.getSortIndex() : 0))
                .toList();

        for (SupplierOrderLine line : sorted) {
            StockProduct product = line.getStockProduct();
            VariableChargeCreateRequest vc = VariableChargeCreateRequest.builder()
                    .date(chargeDate)
                    .mainCategoryId(stockMain.getId())
                    .subCategoryId(product.getSubCategory().getId())
                    .productId(product.getId())
                    .quantity(line.getQuantity())
                    .unitPrice(line.getUnitPriceSnapshot())
                    .amount(line.getLineAmount())
                    .supplier(truncate(supplierLabel, 200))
                    .notes("supplier_order:" + order.getId())
                    .updateStockProductUnitPrice(updateCatalog)
                    .build();
            chargeService.createVariableCharge(vc);
        }

        order.setStatus(SupplierOrderStatus.CONVERTED);
        order.setConvertedAt(LocalDateTime.now());
        supplierOrderRepository.save(order);
        return toDetailResponse(supplierOrderRepository.findFetchedByIdAndStoreId(id, storeId).orElse(order));
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String resolveSupplierLabel(SupplierOrder order) {
        if (order.getSupplier() != null) {
            return order.getSupplier().getName();
        }
        if (order.getSupplierNameSnapshot() != null && !order.getSupplierNameSnapshot().isBlank()) {
            return order.getSupplierNameSnapshot().trim();
        }
        return null;
    }

    private List<SupplierOrderLine> buildLines(SupplierOrder order, Long storeId, List<SupplierOrderLineRequest> lineReqs) {
        List<SupplierOrderLine> lines = new ArrayList<>();
        int n = lineReqs.size();
        for (int i = 0; i < n; i++) {
            SupplierOrderLineRequest req = lineReqs.get(i);
            StockProduct product = stockProductRepository.findById(req.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Stock product not found: " + req.getProductId()));
            if (!product.getStore().getId().equals(storeId)) {
                throw new IllegalArgumentException("Product does not belong to your store");
            }
            BigDecimal lineAmount = resolveLineAmount(req);
            SupplierOrderLine line = SupplierOrderLine.builder()
                    .order(order)
                    .stockProduct(product)
                    .quantity(req.getQuantity())
                    .unitPriceSnapshot(req.getUnitPriceSnapshot().setScale(2, RoundingMode.HALF_UP))
                    .lineAmount(lineAmount)
                    .sortIndex(i)
                    .build();
            lines.add(line);
        }
        return lines;
    }

    private BigDecimal resolveLineAmount(SupplierOrderLineRequest req) {
        if (req.getLineAmount() != null) {
            return req.getLineAmount().setScale(2, RoundingMode.HALF_UP);
        }
        return req.getQuantity()
                .multiply(req.getUnitPriceSnapshot())
                .setScale(2, RoundingMode.HALF_UP);
    }

    private SupplierOrderDetailResponse toDetailResponse(SupplierOrder order) {
        List<SupplierOrderLine> sorted = order.getLines() == null ? List.of() : order.getLines().stream()
                .sorted(Comparator.comparingInt(l -> l.getSortIndex() != null ? l.getSortIndex() : 0))
                .toList();
        BigDecimal total = sorted.stream()
                .map(SupplierOrderLine::getLineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String supplierName = order.getSupplier() != null
                ? order.getSupplier().getName()
                : order.getSupplierNameSnapshot();
        Long supplierId = order.getSupplier() != null ? order.getSupplier().getId() : null;

        List<SupplierOrderLineResponse> lineResponses = new ArrayList<>(sorted.size());
        for (SupplierOrderLine l : sorted) {
            lineResponses.add(SupplierOrderLineResponse.builder()
                    .id(l.getId())
                    .productId(l.getStockProduct().getId())
                    .productName(l.getStockProduct().getName())
                    .quantity(l.getQuantity())
                    .unitPriceSnapshot(l.getUnitPriceSnapshot())
                    .lineAmount(l.getLineAmount())
                    .sortIndex(l.getSortIndex())
                    .build());
        }

        return SupplierOrderDetailResponse.builder()
                .id(order.getId())
                .status(order.getStatus())
                .orderDate(order.getOrderDate())
                .supplierId(supplierId)
                .supplierName(supplierName)
                .messageText(order.getMessageText())
                .totalAmount(total.setScale(2, RoundingMode.HALF_UP))
                .convertedAt(order.getConvertedAt())
                .lines(lineResponses)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
