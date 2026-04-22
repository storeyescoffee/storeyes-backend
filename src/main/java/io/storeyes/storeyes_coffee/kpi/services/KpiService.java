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
import io.storeyes.storeyes_coffee.store.services.DemoStoreDataSourceResolver;
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
    private final DemoStoreDataSourceResolver demoStoreDataSourceResolver;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static double scaledRevenue(Double value, double mult) {
        if (value == null) {
            return 0.0;
        }
        return value * mult;
    }

    private static int scaledQuantity(Integer quantity, double mult) {
        if (quantity == null) {
            return 0;
        }
        return (int) Math.round(quantity * mult);
    }
    
    /**
     * Generate daily report for a store on a specific date
     */
    public DailyReportDTO getDailyReport(Long storeId, LocalDate date) {
        DemoStoreDataSourceResolver.KpiDataContext kpiCtx = demoStoreDataSourceResolver.resolveKpiContext(storeId);
        Long dataStoreId = kpiCtx.dataStoreId();
        double mult = kpiCtx.revenueQuantityMultiplier();

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new RuntimeException("Store not found with id: " + storeId));

        DateDimension dateDimension = dateDimensionRepository.findByDate(date)
                .orElseThrow(() -> new RuntimeException("Date dimension not found for date: " + date));

        Optional<FactKpiDaily> dailyKpiOpt = factKpiDailyRepository.findByStoreIdAndDate(dataStoreId, dateDimension);
        if (dailyKpiOpt.isEmpty()) {
            return buildDailyReportWhenNoKpiRow(store, date, dataStoreId, mult);
        }
        FactKpiDaily dailyKpi = dailyKpiOpt.get();

        List<FactKpiHourly> hourlyKpis = factKpiHourlyRepository.findByStoreIdAndDateOrderByHourAsc(dataStoreId, dateDimension);

        List<FactKpiProductDaily> productKpis = factKpiProductDailyRepository.findByStoreIdAndDate(dataStoreId, dateDimension);

        List<FactKpiCategoryDaily> categoryKpis = factKpiCategoryDailyRepository.findByStoreIdAndDate(dataStoreId, dateDimension);

        List<FactKpiServerDaily> serverKpis = factKpiServerDailyRepository.findByStoreIdAndDateOrderByRevenueDesc(dataStoreId, dateDimension);

        double totalTTC = scaledRevenue(dailyKpi.getTotalRevenueTtc(), mult);
        Double tpe = null;
        Double espece = null;
        var breakdownOpt = dailyRevenuePaymentBreakdownRepository.findByStoreIdAndDate(dataStoreId, dateDimension);
        if (breakdownOpt.isPresent()) {
            tpe = scaledRevenue(breakdownOpt.get().getTpe(), mult);
            espece = Math.max(0, totalTTC - (tpe != null ? tpe : 0));
        }
        int transactions = dailyKpi.getTransactions() != null ? dailyKpi.getTransactions() : 0;
        double avgTransactionValue = transactions > 0 ? totalTTC / transactions : scaledRevenue(dailyKpi.getAverageTransactionValue(), mult);
        RevenueDTO revenue = RevenueDTO.builder()
                .totalTTC(totalTTC)
                .totalHT(scaledRevenue(dailyKpi.getTotalRevenueHt(), mult))
                .transactions(transactions)
                .avgTransactionValue(avgTransactionValue)
                .revenuePerTransaction(avgTransactionValue)
                .tpe(tpe)
                .espece(espece)
                .build();

        Map<Integer, FactKpiHourly> hourlyMap = hourlyKpis.stream()
                .collect(Collectors.toMap(FactKpiHourly::getHour, h -> h));

        List<HourlyDataDTO> hourlyData = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            FactKpiHourly h = hourlyMap.get(hour);
            double revenueHour = (h != null && h.getRevenue() != null) ? scaledRevenue(h.getRevenue(), mult) : 0.0;
            Integer transactionsHour = (h != null && h.getTransactions() != null) ? h.getTransactions() : 0;
            int itemsSoldHour = (h != null && h.getQuantity() != null) ? scaledQuantity(h.getQuantity(), mult) : 0;

            hourlyData.add(HourlyDataDTO.builder()
                    .hour(formatHour(hour))
                    .revenue(revenueHour)
                    .transactions(transactionsHour)
                    .itemsSold(itemsSoldHour)
                    .build());
        }

        List<TopProductDTO> topProductsByQuantity = new ArrayList<>();
        productKpis.stream()
                .sorted((a, b) -> b.getQuantity().compareTo(a.getQuantity()))
                .limit(10)
                .forEach(p -> {
                    int rank = topProductsByQuantity.size() + 1;
                    topProductsByQuantity.add(TopProductDTO.builder()
                            .rank(rank)
                            .name(p.getProductName())
                            .quantity(scaledQuantity(p.getQuantity(), mult))
                            .build());
                });

        List<TopProductDTO> topProductsByRevenue = new ArrayList<>();
        productKpis.stream()
                .sorted((a, b) -> b.getRevenue().compareTo(a.getRevenue()))
                .limit(10)
                .forEach(p -> {
                    int rank = topProductsByRevenue.size() + 1;
                    topProductsByRevenue.add(TopProductDTO.builder()
                            .rank(rank)
                            .name(p.getProductName())
                            .revenue(scaledRevenue(p.getRevenue(), mult))
                            .build());
                });

        double totalRevenue = scaledRevenue(dailyKpi.getTotalRevenueTtc(), mult);
        List<CategoryAnalysisDTO> categoryAnalysis = categoryKpis.stream()
                .map(c -> {
                    double catRev = scaledRevenue(c.getRevenue(), mult);
                    return CategoryAnalysisDTO.builder()
                            .category(c.getCategory())
                            .revenue(catRev)
                            .quantity(scaledQuantity(c.getQuantity(), mult))
                            .transactions(c.getTransactions())
                            .percentageOfRevenue(totalRevenue > 0 ? (catRev / totalRevenue) * 100 : 0.0)
                            .build();
                })
                .sorted((a, b) -> Double.compare(b.getRevenue(), a.getRevenue()))
                .collect(Collectors.toList());

        List<StaffPerformanceDTO> staffPerformance = serverKpis.stream()
                .map(s -> {
                    double rev = scaledRevenue(s.getRevenue(), mult);
                    int tx = s.getTransactions() != null ? s.getTransactions() : 0;
                    return StaffPerformanceDTO.builder()
                            .name(s.getServer())
                            .revenue(rev)
                            .transactions(tx)
                            .avgValue(tx > 0 ? rev / tx : 0.0)
                            .share(totalRevenue > 0 ? (rev / totalRevenue) * 100 : 0.0)
                            .build();
                })
                .collect(Collectors.toList());

        List<PeakPeriodDTO> peakPeriods = calculatePeakPeriods(hourlyKpis, totalRevenue, mult);

        InsightsDTO insights = calculateInsights(hourlyKpis, productKpis, peakPeriods, dataStoreId, date, mult);
        
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
        DemoStoreDataSourceResolver.KpiDataContext kpiCtx = demoStoreDataSourceResolver.resolveKpiContext(storeId);
        Long dataStoreId = kpiCtx.dataStoreId();
        Store store = storeRepository.findById(dataStoreId)
                .orElseThrow(() -> new RuntimeException("Store not found with id: " + dataStoreId));
        DateDimension dateDimension = dateDimensionRepository.findByDate(date)
                .orElseThrow(() -> new RuntimeException("Date dimension not found for date: " + date));
        FactKpiDaily dailyKpi = factKpiDailyRepository.findByStoreIdAndDate(dataStoreId, dateDimension)
                .orElseThrow(() -> new RuntimeException("Daily KPI not found for store: " + dataStoreId + " and date: " + date));
        Double totalTTC = dailyKpi.getTotalRevenueTtc();
        if (tpe > totalTTC) {
            throw new RuntimeException("TPE cannot exceed total revenue (TTC): " + totalTTC);
        }
        DailyRevenuePaymentBreakdown breakdown = dailyRevenuePaymentBreakdownRepository
                .findByStoreIdAndDate(dataStoreId, dateDimension)
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
    private List<PeakPeriodDTO> calculatePeakPeriods(List<FactKpiHourly> hourlyKpis, double totalRevenue, double mult) {
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
                    periodRevenue += scaledRevenue(hourly.getRevenue(), mult);
                    periodTransactions += hourly.getTransactions() != null ? hourly.getTransactions() : 0;
                    periodItemsSold += scaledQuantity(hourly.getQuantity(), mult);
                }
            }
            
            if (periodRevenue > 0) {
                double share = totalRevenue > 0 ? (periodRevenue / totalRevenue) * 100.0 : 0.0;
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
                                         Long dataStoreId,
                                         LocalDate date,
                                         double mult) {
        // Peak hour
        FactKpiHourly peakHour = hourlyKpis.stream()
                .max(Comparator.comparing(FactKpiHourly::getRevenue))
                .orElse(null);

        InsightsDTO.PeakHourDTO peakHourDTO = null;
        if (peakHour != null) {
            peakHourDTO = InsightsDTO.PeakHourDTO.builder()
                    .time(formatHour(peakHour.getHour()))
                    .revenue(scaledRevenue(peakHour.getRevenue(), mult))
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
                    .quantity(scaledQuantity(bestProduct.getQuantity(), mult))
                    .build();
        }

        // Highest value transaction - approximate using avg * 2
        FactKpiDaily dailyKpi = factKpiDailyRepository.findByStoreIdAndDate(
                dataStoreId,
                dateDimensionRepository.findByDate(date).orElse(null)
        ).orElse(null);

        Double highestValueTransaction = null;
        if (dailyKpi != null) {
            int tx = dailyKpi.getTransactions() != null ? dailyKpi.getTransactions() : 0;
            double scaledAvg = tx > 0
                    ? scaledRevenue(dailyKpi.getTotalRevenueTtc(), mult) / tx
                    : scaledRevenue(dailyKpi.getAverageTransactionValue(), mult);
            highestValueTransaction = scaledAvg * 2;
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
        InsightsDTO.RevenueComparisonDTO revenueComparison = calculateRevenueComparison(dataStoreId, date);
        
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
    private InsightsDTO.RevenueComparisonDTO calculateRevenueComparison(Long dataStoreId, LocalDate date) {
        DateDimension currentDate = dateDimensionRepository.findByDate(date).orElse(null);
        if (currentDate == null) {
            return InsightsDTO.RevenueComparisonDTO.builder()
                    .vsPreviousDay(0.0)
                    .vsPreviousWeek(0.0)
                    .build();
        }

        FactKpiDaily currentDaily = factKpiDailyRepository.findByStoreIdAndDate(dataStoreId, currentDate).orElse(null);
        if (currentDaily == null) {
            return InsightsDTO.RevenueComparisonDTO.builder()
                    .vsPreviousDay(0.0)
                    .vsPreviousWeek(0.0)
                    .build();
        }

        Double currentRevenue = currentDaily.getTotalRevenueTtc();

        LocalDate previousDay = date.minusDays(1);
        DateDimension previousDayDim = dateDimensionRepository.findByDate(previousDay).orElse(null);
        Double previousDayRevenue = null;
        if (previousDayDim != null) {
            FactKpiDaily previousDaily = factKpiDailyRepository.findByStoreIdAndDate(dataStoreId, previousDayDim).orElse(null);
            if (previousDaily != null) {
                previousDayRevenue = previousDaily.getTotalRevenueTtc();
            }
        }

        LocalDate previousWeek = date.minusWeeks(1);
        DateDimension previousWeekDim = dateDimensionRepository.findByDate(previousWeek).orElse(null);
        Double previousWeekRevenue = null;
        if (previousWeekDim != null) {
            FactKpiDaily previousWeekDaily = factKpiDailyRepository.findByStoreIdAndDate(dataStoreId, previousWeekDim).orElse(null);
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
     * When no {@link FactKpiDaily} row exists yet for the KPI source store and date (e.g. future day or ETL lag).
     */
    private DailyReportDTO buildDailyReportWhenNoKpiRow(Store store, LocalDate date, Long dataStoreId, double mult) {
        List<HourlyDataDTO> hourlyData = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            hourlyData.add(HourlyDataDTO.builder()
                    .hour(formatHour(hour))
                    .revenue(0.0)
                    .transactions(0)
                    .itemsSold(0)
                    .build());
        }
        RevenueDTO revenue = RevenueDTO.builder()
                .totalTTC(0.0)
                .totalHT(0.0)
                .transactions(0)
                .avgTransactionValue(0.0)
                .revenuePerTransaction(0.0)
                .tpe(null)
                .espece(null)
                .build();
        List<PeakPeriodDTO> peakPeriods = Collections.emptyList();
        InsightsDTO insights = calculateInsights(
                Collections.emptyList(),
                Collections.emptyList(),
                peakPeriods,
                dataStoreId,
                date,
                mult);
        return DailyReportDTO.builder()
                .date(date.format(DATE_FORMATTER))
                .businessName(store.getName())
                .revenue(revenue)
                .hourlyData(hourlyData)
                .topProductsByQuantity(Collections.emptyList())
                .topProductsByRevenue(Collections.emptyList())
                .categoryAnalysis(Collections.emptyList())
                .staffPerformance(Collections.emptyList())
                .peakPeriods(peakPeriods)
                .insights(insights)
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

