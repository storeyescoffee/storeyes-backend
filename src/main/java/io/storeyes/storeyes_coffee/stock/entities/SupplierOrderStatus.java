package io.storeyes.storeyes_coffee.stock.entities;

public enum SupplierOrderStatus {
    PENDING,
    VALID,
    REJECTED,
    // Legacy statuses kept for backward compatibility with rows
    // created before V19 migration.
    DRAFT,
    SENT,
    CONVERTED
}
