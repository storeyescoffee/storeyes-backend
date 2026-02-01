package io.storeyes.storeyes_coffee.sales.cron;

import io.storeyes.storeyes_coffee.alerts.entities.Alert;
import io.storeyes.storeyes_coffee.alerts.repositories.AlertRepository;
import io.storeyes.storeyes_coffee.sales.dto.CoffeeSalesHourlyDTO;
import io.storeyes.storeyes_coffee.sales.entities.Sales;
import io.storeyes.storeyes_coffee.sales.repositories.SalesRepository;
import io.storeyes.storeyes_coffee.store.entities.Store;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SalesProcessor {
    
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final AlertRepository alertRepository;
    private final SalesRepository salesRepository;
    
    /**
     * Number of sales to collect before each alert
     */
    private static final int SALES_BEFORE_ALERT_COUNT = 3;
    
    /**
     * Get all records from coffee_sales_hourly table filtered by store code and date
     * @param storeCode The store code to filter by
     * @param date The date to filter by (sale_date)
     * @return List of CoffeeSalesHourlyDTO containing coffee sales hourly data
     */
    private List<CoffeeSalesHourlyDTO> getCoffeeSalesHourlyRecords(String storeCode, LocalDate date) {
        String sql = "SELECT id, sale_date, hour, sale_time, coffee_name, quantity, price, " +
                     "total_price, category, created_at, coffee_shop_name " +
                     "FROM coffee_sales_hourly " +
                     "WHERE coffee_shop_name = :storeCode AND sale_date = :date " +
                     "ORDER BY sale_date DESC, hour DESC";
        
        Map<String, Object> params = new HashMap<>();
        params.put("storeCode", storeCode);
        params.put("date", date);
        
        return namedParameterJdbcTemplate.query(sql, params, new CoffeeSalesHourlyRowMapper());
    }
    
    /**
     * RowMapper for CoffeeSalesHourlyDTO
     */
    private static class CoffeeSalesHourlyRowMapper implements RowMapper<CoffeeSalesHourlyDTO> {
        @Override
        public CoffeeSalesHourlyDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
            // Parse sale_time string to LocalTime
            LocalTime saleTime = null;
            String saleTimeStr = rs.getString("sale_time");
            if (saleTimeStr != null && !saleTimeStr.isEmpty()) {
                if (saleTimeStr.length() == 5) {
                    saleTime = LocalTime.parse(saleTimeStr, DateTimeFormatter.ofPattern("HH:mm"));
                } else {
                    saleTime = LocalTime.parse(saleTimeStr);
                }
            }
            
            return CoffeeSalesHourlyDTO.builder()
                    .id(rs.getLong("id"))
                    .saleDate(rs.getObject("sale_date", LocalDate.class))
                    .hour(rs.getInt("hour"))
                    .saleTime(saleTime)
                    .coffeeName(rs.getString("coffee_name"))
                    .quantity(rs.getBigDecimal("quantity"))
                    .price(rs.getBigDecimal("price"))
                    .totalPrice(rs.getBigDecimal("total_price"))
                    .category(rs.getString("category"))
                    .createdAt(rs.getObject("created_at", LocalDateTime.class))
                    .coffeeShopName(rs.getString("coffee_shop_name"))
                    .build();
        }
    }

    /**
     * Create Sales entities from coffee sales hourly data, linking the last 3 sales before each alert
     * Optimized version leveraging pre-sorted alerts: O(n log n + m * log n + m * 3)
     * - Sort sales once: O(n log n)
     * - Use binary search to find insertion point for each alert: O(m * log n)
     * - Collect 3 sales per alert: O(m * 3)
     * @param coffeeSalesHourly List of coffee sales hourly records from database
     * @param alerts List of Alert entities (already sorted chronologically)
     * @return List of Sales entities created
     */
    private List<Sales> createSalesFromCoffeeSalesHourly(
        List<CoffeeSalesHourlyDTO> coffeeSalesHourly,
        List<Alert> alerts
    ) {
        if (coffeeSalesHourly.isEmpty() || alerts.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Sales> salesList = new ArrayList<>();
        
        // Sort sales once by soldAt descending (most recent first) - O(n log n)
        List<CoffeeSalesHourlyDTO> sortedSales = new ArrayList<>(coffeeSalesHourly);
        sortedSales.sort((a, b) -> {
            LocalDateTime soldAtA = a.getSoldAt();
            LocalDateTime soldAtB = b.getSoldAt();
            if (soldAtA == null && soldAtB == null) return 0;
            if (soldAtA == null) return 1;
            if (soldAtB == null) return -1;
            return soldAtB.compareTo(soldAtA);
        });
        
        // For each alert (already sorted chronologically), find the 3 last sales before it
        // Use binary search to find insertion point: O(m * log n) instead of O(m * n)
        for (Alert alert : alerts) {
            LocalDateTime alertDate = alert.getAlertDate();
            
            // Binary search to find the first sale that is before the alert date
            int insertionPoint = findFirstSaleBeforeDate(sortedSales, alertDate);
            
            // Collect up to SALES_BEFORE_ALERT_COUNT sales starting from the insertion point
            List<CoffeeSalesHourlyDTO> salesBeforeAlert = new ArrayList<>(SALES_BEFORE_ALERT_COUNT);
            for (int i = insertionPoint; i < sortedSales.size() && salesBeforeAlert.size() < SALES_BEFORE_ALERT_COUNT; i++) {
                CoffeeSalesHourlyDTO sale = sortedSales.get(i);
                LocalDateTime soldAt = sale.getSoldAt();
                if (soldAt != null && soldAt.isBefore(alertDate)) {
                    salesBeforeAlert.add(sale);
                } else {
                    // Since sales are sorted descending, if we encounter a sale >= alertDate,
                    // all remaining sales will also be >= alertDate, so we can break
                    break;
                }
            }
            
            // Create Sales entities for these sales
            for (CoffeeSalesHourlyDTO csh : salesBeforeAlert) {
                Sales sales = Sales.builder()
                    .alert(alert)
                    .productName(csh.getCoffeeName())
                    .quantity(csh.getQuantity() != null ? csh.getQuantity().doubleValue() : null)
                    .price(csh.getPrice() != null ? csh.getPrice().doubleValue() : null)
                    .totalPrice(csh.getTotalPrice() != null ? csh.getTotalPrice().doubleValue() : null)
                    .soldAt(csh.getSoldAt())
                    .category(csh.getCategory())
                    .build();
                
                salesList.add(sales);
            }
        }
        
        return salesList;
    }
    
    /**
     * Binary search to find the first sale that is before the given date
     * Sales are sorted descending (most recent first)
     * Returns the index of the first sale where soldAt < alertDate
     * @param sortedSales List of sales sorted by soldAt descending
     * @param alertDate The alert date to search for
     * @return Index of first sale before alertDate, or sales.size() if no such sale exists
     */
    private int findFirstSaleBeforeDate(List<CoffeeSalesHourlyDTO> sortedSales, LocalDateTime alertDate) {
        int left = 0;
        int right = sortedSales.size();
        
        while (left < right) {
            int mid = left + (right - left) / 2;
            LocalDateTime soldAt = sortedSales.get(mid).getSoldAt();
            
            if (soldAt != null && soldAt.isBefore(alertDate)) {
                // This sale is before alertDate, but we need to find the FIRST one
                // Continue searching left to find an earlier sale
                right = mid;
            } else {
                // This sale is >= alertDate or null, search right
                left = mid + 1;
            }
        }
        
        return left;
    }
    
    
    /**
     * Process sales for a store: fetch coffee sales hourly data and alerts,
     * map them together, and save the sales to the database
     * @param store The store entity to process sales for
     * @param date The date to filter coffee sales hourly records and alerts
     * @return Number of sales records created and saved
     */
    @Transactional
    public int processSalesForStore(Store store, LocalDate date) {
        // Get store code
        String storeCode = store.getCode();
        if (storeCode == null || storeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Store code cannot be null or empty");
        }
        
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        
        // Get coffee sales hourly records for this store and date
        List<CoffeeSalesHourlyDTO> coffeeSalesHourly = getCoffeeSalesHourlyRecords(storeCode, date);
        
        if (coffeeSalesHourly.isEmpty()) {
            // No coffee sales data found, return 0
            return 0;
        }
        
        // Get alerts for this store and date, sorted chronologically (oldest first)
        List<Alert> alerts = alertRepository.findByAlertDateAndStoreIdOrderByAlertDateAsc(date, store.getId());
        
        if (alerts.isEmpty()) {
            // No alerts found, return 0
            return 0;
        }
        
        // Map coffee sales hourly data to alerts and create Sales entities
        List<Sales> salesList = createSalesFromCoffeeSalesHourly(coffeeSalesHourly, alerts);
        
        if (salesList.isEmpty()) {
            return 0;
        }
        
        // Save all sales to database
        salesRepository.saveAll(salesList);
        
        return salesList.size();
    }

}
