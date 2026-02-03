package io.storeyes.storeyes_coffee.kpi.services;

import io.storeyes.storeyes_coffee.kpi.dto.*;
import io.storeyes.storeyes_coffee.kpi.entities.DailyRevenuePaymentBreakdown;
import io.storeyes.storeyes_coffee.kpi.entities.DateDimension;
import io.storeyes.storeyes_coffee.kpi.entities.FactKpiCategoryDaily;
import io.storeyes.storeyes_coffee.kpi.entities.FactKpiDaily;
import io.storeyes.storeyes_coffee.kpi.entities.FactKpiHourly;
import io.storeyes.storeyes_coffee.kpi.entities.FactKpiProductDaily;
import io.storeyes.storeyes_coffee.kpi.entities.FactKpiServerDaily;
import io.storeyes.storeyes_coffee.kpi.repositories.*;
import io.storeyes.storeyes_coffee.store.entities.Store;
import io.storeyes.storeyes_coffee.store.repositories.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KpiService {
    
    private final FactKpiDailyRepository factKpiDailyRepository;
    private final FactKpiHourlyRepository factKpiHourlyRepository;
    private final FactKpiProductDailyRepository factKpiProductDailyRepository;
    private final FactKpiCategoryDailyRepository factKpiCategoryDailyRepository;
    private final FactKpiServerDailyRepository factKpiServerDailyRepository;
    private final DateDimensionRepository dateDimensionRepository;
    private final DailyRevenuePaymentBreakdownRepository dailyRevenuePaymentBreakdownRepository;
    private final StoreRepository storeRepository;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    /**
     * Generate daily report for a store on a specific date
     */
    public DailyReportDTO getDailyReport(Long storeId, LocalDate date) {
        // Get store
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new RuntimeException("Store not found with id: " + storeId));
        
        // Get date dimension
        DateDimension dateDimension = dateDimensionRepository.findByDate(date)
                .orElseThrow(() -> new RuntimeException("Date dimension not found for date: " + date));
        
        // Get daily KPI
        FactKpiDaily dailyKpi = factKpiDailyRepository.findByStoreIdAndDate(storeId, dateDimension)
                .orElseThrow(() -> new RuntimeException("Daily KPI not found for store: " + storeId + " and date: " + date));
        
        // Get hourly data
        List<FactKpiHourly> hourlyKpis = factKpiHourlyRepository.findByStoreIdAndDateOrderByHourAsc(storeId, dateDimension);
        
        // Get product data
        List<FactKpiProductDaily> productKpis = factKpiProductDailyRepository.findByStoreIdAndDate(storeId, dateDimension);
        
        // Get category data
        List<FactKpiCategoryDaily> categoryKpis = factKpiCategoryDailyRepository.findByStoreIdAndDate(storeId, dateDimension);
        
        // Get server data
        List<FactKpiServerDaily> serverKpis = factKpiServerDailyRepository.findByStoreIdAndDateOrderByRevenueDesc(storeId, dateDimension);
        
        // Build revenue DTO (include TPE and Espèce from breakdown if present)
        Double totalTTC = dailyKpi.getTotalRevenueTtc();
        Double tpe = null;
        Double espece = null;
        var breakdownOpt = dailyRevenuePaymentBreakdownRepository.findByStoreIdAndDate(storeId, dateDimension);
        if (breakdownOpt.isPresent()) {
            tpe = breakdownOpt.get().getTpe();
            espece = Math.max(0, totalTTC - (tpe != null ? tpe : 0));
        }
        RevenueDTO revenue = RevenueDTO.builder()
                .totalTTC(totalTTC)
                .totalHT(dailyKpi.getTotalRevenueHt())
                .transactions(dailyKpi.getTransactions())
                .avgTransactionValue(dailyKpi.getAverageTransactionValue())
                .revenuePerTransaction(dailyKpi.getAverageTransactionValue())
                .tpe(tpe)
                .espece(espece)
                .build();
        
        // Build hourly data DTOs
        List<HourlyDataDTO> hourlyData = hourlyKpis.stream()
                .map(h -> HourlyDataDTO.builder()
                        .hour(formatHour(h.getHour()))
                        .revenue(h.getRevenue())
                        .transactions(h.getTransactions())
                        .itemsSold(h.getQuantity())
                        .build())
                .collect(Collectors.toList());
        
        // Build top products by quantity
        List<TopProductDTO> topProductsByQuantity = new ArrayList<>();
        productKpis.stream()
                .sorted((a, b) -> b.getQuantity().compareTo(a.getQuantity()))
                .limit(10)
                .forEach(p -> {
                    int rank = topProductsByQuantity.size() + 1;
                    topProductsByQuantity.add(TopProductDTO.builder()
                            .rank(rank)
                            .name(p.getProductName())
                            .quantity(p.getQuantity())
                            .build());
                });
        
        // Build top products by revenue
        List<TopProductDTO> topProductsByRevenue = new ArrayList<>();
        productKpis.stream()
                .sorted((a, b) -> b.getRevenue().compareTo(a.getRevenue()))
                .limit(10)
                .forEach(p -> {
                    int rank = topProductsByRevenue.size() + 1;
                    topProductsByRevenue.add(TopProductDTO.builder()
                            .rank(rank)
                            .name(p.getProductName())
                            .revenue(p.getRevenue())
                            .build());
                });
        
        // Build category analysis
        Double totalRevenue = dailyKpi.getTotalRevenueTtc();
        List<CategoryAnalysisDTO> categoryAnalysis = categoryKpis.stream()
                .map(c -> CategoryAnalysisDTO.builder()
                        .category(c.getCategory())
                        .revenue(c.getRevenue())
                        .quantity(c.getQuantity())
                        .transactions(c.getTransactions())
                        .percentageOfRevenue(totalRevenue > 0 ? (c.getRevenue() / totalRevenue) * 100 : 0.0)
                        .build())
                .sorted((a, b) -> b.getRevenue().compareTo(a.getRevenue()))
                .collect(Collectors.toList());
        
        // Build staff performance
        List<StaffPerformanceDTO> staffPerformance = serverKpis.stream()
                .map(s -> StaffPerformanceDTO.builder()
                        .name(s.getServer())
                        .revenue(s.getRevenue())
                        .transactions(s.getTransactions())
                        .avgValue(s.getTransactions() > 0 ? s.getRevenue() / s.getTransactions() : 0.0)
                        .share(totalRevenue > 0 ? (s.getRevenue() / totalRevenue) * 100 : 0.0)
                        .build())
                .collect(Collectors.toList());
        
        // Build peak periods
        List<PeakPeriodDTO> peakPeriods = calculatePeakPeriods(hourlyKpis, totalRevenue);
        
        // Build insights
        InsightsDTO insights = calculateInsights(hourlyKpis, productKpis, peakPeriods, storeId, date);
        
        // Build and return daily report
        return DailyReportDTO.builder()
                .date(date.format(DATE_FORMATTER))
                .businessName(store.getName())
                .revenue(revenue)
                .hourlyData(hourlyData)
                .topProductsByQuantity(topProductsByQuantity)
                .topProductsByRevenue(topProductsByRevenue)
                .categoryAnalysis(categoryAnalysis)
                .staffPerformance(staffPerformance)
                .peakPeriods(peakPeriods)
                .insights(insights)
                .build();
    }
    
    /**
     * Update TPE (card payment) for a daily report. Espèce = TTC - TPE (computed).
     * @param storeId store ID
     * @param date date for the report
     * @param tpe TPE value (card payment). Must be >= 0 and <= total TTC.
     */
    public void updateRevenueBreakdown(Long storeId, LocalDate date, Double tpe) {
        if (tpe == null || tpe < 0) {
            throw new RuntimeException("TPE must be >= 0");
        }
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new RuntimeException("Store not found with id: " + storeId));
        DateDimension dateDimension = dateDimensionRepository.findByDate(date)
                .orElseThrow(() -> new RuntimeException("Date dimension not found for date: " + date));
        FactKpiDaily dailyKpi = factKpiDailyRepository.findByStoreIdAndDate(storeId, dateDimension)
                .orElseThrow(() -> new RuntimeException("Daily KPI not found for store: " + storeId + " and date: " + date));
        Double totalTTC = dailyKpi.getTotalRevenueTtc();
        if (tpe > totalTTC) {
            throw new RuntimeException("TPE cannot exceed total revenue (TTC): " + totalTTC);
        }
        DailyRevenuePaymentBreakdown breakdown = dailyRevenuePaymentBreakdownRepository
                .findByStoreIdAndDate(storeId, dateDimension)
                .orElse(DailyRevenuePaymentBreakdown.builder()
                        .store(store)
                        .date(dateDimension)
                        .tpe(0.0)
                        .build());
        breakdown.setTpe(tpe);
        dailyRevenuePaymentBreakdownRepository.save(breakdown);
    }

    /**
     * Format hour integer to "HH:mm" string
     */
    private String formatHour(Integer hour) {
        return String.format("%02d:00", hour);
    }
    
    /**
     * Calculate peak periods from hourly data
     */
    private List<PeakPeriodDTO> calculatePeakPeriods(List<FactKpiHourly> hourlyKpis, Double totalRevenue) {
        // Define time ranges
        List<PeriodRange> periodRanges = Arrays.asList(
                new PeriodRange("Early Morning", "05:00-07:00", 5, 7),
                new PeriodRange("Morning Rush", "07:00-10:00", 7, 10),
                new PeriodRange("Mid-Morning", "10:00-12:00", 10, 12),
                new PeriodRange("Lunch Period", "12:00-14:00", 12, 14),
                new PeriodRange("Afternoon (2-4 PM)", "14:00-16:00", 14, 16),
                new PeriodRange("Evening", "16:00-17:00", 16, 17),
                new PeriodRange("Late Evening", "17:00-22:00", 17, 22)
        );
        
        // Create map for quick lookup
        Map<Integer, FactKpiHourly> hourlyMap = hourlyKpis.stream()
                .collect(Collectors.toMap(FactKpiHourly::getHour, h -> h));
        
        List<PeakPeriodDTO> peakPeriods = new ArrayList<>();
        
        for (PeriodRange range : periodRanges) {
            double periodRevenue = 0.0;
            int periodTransactions = 0;
            int periodItemsSold = 0;
            
            // Sum data for hours in range (inclusive start, exclusive end)
            for (int hour = range.startHour; hour < range.endHour; hour++) {
                FactKpiHourly hourly = hourlyMap.get(hour);
                if (hourly != null) {
                    periodRevenue += hourly.getRevenue();
                    periodTransactions += hourly.getTransactions();
                    periodItemsSold += hourly.getQuantity();
                }
            }
            
            if (periodRevenue > 0) {
                double share = totalRevenue > 0 ? (periodRevenue / totalRevenue) * 100 : 0.0;
                String status = determinePeriodStatus(share);
                
                peakPeriods.add(PeakPeriodDTO.builder()
                        .period(range.name)
                        .timeRange(range.timeRange)
                        .revenue(periodRevenue)
                        .transactions(periodTransactions)
                        .itemsSold(periodItemsSold)
                        .share(share)
                        .status(status)
                        .build());
            }
        }
        
        // Sort by revenue descending
        peakPeriods.sort((a, b) -> b.getRevenue().compareTo(a.getRevenue()));
        
        return peakPeriods;
    }
    
    /**
     * Determine period status based on share percentage
     */
    private String determinePeriodStatus(double share) {
        if (share >= 20.0) {
            return "peak";
        } else if (share >= 10.0) {
            return "moderate";
        } else {
            return "low";
        }
    }
    
    /**
     * Calculate insights
     */
    private InsightsDTO calculateInsights(List<FactKpiHourly> hourlyKpis, 
                                         List<FactKpiProductDaily> productKpis,
                                         List<PeakPeriodDTO> peakPeriods,
                                         Long storeId, 
                                         LocalDate date) {
        // Peak hour
        FactKpiHourly peakHour = hourlyKpis.stream()
                .max(Comparator.comparing(FactKpiHourly::getRevenue))
                .orElse(null);
        
        InsightsDTO.PeakHourDTO peakHourDTO = null;
        if (peakHour != null) {
            peakHourDTO = InsightsDTO.PeakHourDTO.builder()
                    .time(formatHour(peakHour.getHour()))
                    .revenue(peakHour.getRevenue())
                    .build();
        }
        
        // Best selling product
        FactKpiProductDaily bestProduct = productKpis.stream()
                .max(Comparator.comparing(FactKpiProductDaily::getQuantity))
                .orElse(null);
        
        InsightsDTO.BestSellingProductDTO bestSellingProductDTO = null;
        if (bestProduct != null) {
            bestSellingProductDTO = InsightsDTO.BestSellingProductDTO.builder()
                    .name(bestProduct.getProductName())
                    .quantity(bestProduct.getQuantity())
                    .build();
        }
        
        // Highest value transaction - approximate using avg * 2
        FactKpiDaily dailyKpi = factKpiDailyRepository.findByStoreIdAndDate(
                storeId, 
                dateDimensionRepository.findByDate(date).orElse(null)
        ).orElse(null);
        
        Double highestValueTransaction = null;
        if (dailyKpi != null) {
            // Approximate: use average * 2 as highest transaction
            highestValueTransaction = dailyKpi.getAverageTransactionValue() * 2;
        }
        
        // Busiest period
        PeakPeriodDTO busiestPeriod = peakPeriods.stream()
                .max(Comparator.comparing(PeakPeriodDTO::getTransactions))
                .orElse(null);
        
        InsightsDTO.BusiestPeriodDTO busiestPeriodDTO = null;
        if (busiestPeriod != null) {
            busiestPeriodDTO = InsightsDTO.BusiestPeriodDTO.builder()
                    .period(busiestPeriod.getPeriod())
                    .transactions(busiestPeriod.getTransactions())
                    .build();
        }
        
        // Revenue comparison
        InsightsDTO.RevenueComparisonDTO revenueComparison = calculateRevenueComparison(storeId, date);
        
        return InsightsDTO.builder()
                .peakHour(peakHourDTO)
                .bestSellingProduct(bestSellingProductDTO)
                .highestValueTransaction(highestValueTransaction)
                .busiestPeriod(busiestPeriodDTO)
                .revenueComparison(revenueComparison)
                .build();
    }
    
    /**
     * Calculate revenue comparison with previous day and week
     */
    private InsightsDTO.RevenueComparisonDTO calculateRevenueComparison(Long storeId, LocalDate date) {
        DateDimension currentDate = dateDimensionRepository.findByDate(date).orElse(null);
        if (currentDate == null) {
            return InsightsDTO.RevenueComparisonDTO.builder()
                    .vsPreviousDay(0.0)
                    .vsPreviousWeek(0.0)
                    .build();
        }
        
        FactKpiDaily currentDaily = factKpiDailyRepository.findByStoreIdAndDate(storeId, currentDate).orElse(null);
        if (currentDaily == null) {
            return InsightsDTO.RevenueComparisonDTO.builder()
                    .vsPreviousDay(0.0)
                    .vsPreviousWeek(0.0)
                    .build();
        }
        
        Double currentRevenue = currentDaily.getTotalRevenueTtc();
        
        // Previous day
        LocalDate previousDay = date.minusDays(1);
        DateDimension previousDayDim = dateDimensionRepository.findByDate(previousDay).orElse(null);
        Double previousDayRevenue = null;
        if (previousDayDim != null) {
            FactKpiDaily previousDaily = factKpiDailyRepository.findByStoreIdAndDate(storeId, previousDayDim).orElse(null);
            if (previousDaily != null) {
                previousDayRevenue = previousDaily.getTotalRevenueTtc();
            }
        }
        
        // Previous week (same day, previous week)
        LocalDate previousWeek = date.minusWeeks(1);
        DateDimension previousWeekDim = dateDimensionRepository.findByDate(previousWeek).orElse(null);
        Double previousWeekRevenue = null;
        if (previousWeekDim != null) {
            FactKpiDaily previousWeekDaily = factKpiDailyRepository.findByStoreIdAndDate(storeId, previousWeekDim).orElse(null);
            if (previousWeekDaily != null) {
                previousWeekRevenue = previousWeekDaily.getTotalRevenueTtc();
            }
        }
        
        // Calculate percentages
        Double vsPreviousDay = previousDayRevenue != null && previousDayRevenue > 0
                ? ((currentRevenue - previousDayRevenue) / previousDayRevenue) * 100
                : 0.0;
        
        Double vsPreviousWeek = previousWeekRevenue != null && previousWeekRevenue > 0
                ? ((currentRevenue - previousWeekRevenue) / previousWeekRevenue) * 100
                : 0.0;
        
        return InsightsDTO.RevenueComparisonDTO.builder()
                .vsPreviousDay(vsPreviousDay)
                .vsPreviousWeek(vsPreviousWeek)
                .build();
    }
    
    /**
     * Helper class for period ranges
     */
    private static class PeriodRange {
        String name;
        String timeRange;
        int startHour;
        int endHour;
        
        PeriodRange(String name, String timeRange, int startHour, int endHour) {
            this.name = name;
            this.timeRange = timeRange;
            this.startHour = startHour;
            this.endHour = endHour;
        }
    }
}

