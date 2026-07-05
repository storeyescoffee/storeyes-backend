package io.storeyes.storeyes_coffee.kpi.services;

import io.storeyes.storeyes_coffee.kpi.dto.*;
import io.storeyes.storeyes_coffee.kpi.entities.DailyRevenuePaymentBreakdown;
import io.storeyes.storeyes_coffee.kpi.entities.DateDimension;
import io.storeyes.storeyes_coffee.kpi.entities.FactKpiDaily;
import io.storeyes.storeyes_coffee.kpi.entities.FactKpiHourly;
import io.storeyes.storeyes_coffee.kpi.entities.FactKpiServerDaily;
import io.storeyes.storeyes_coffee.kpi.repositories.*;
import io.storeyes.storeyes_coffee.sales.repositories.CoffeeSalesHourlyRepository;
import io.storeyes.storeyes_coffee.store.entities.Store;
import io.storeyes.storeyes_coffee.store.repositories.StoreRepository;
import io.storeyes.storeyes_coffee.store.services.DemoStoreDataSourceResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Locale;
import java.util.LinkedHashMap;

@Service
@RequiredArgsConstructor
public class KpiService {
    
    private final FactKpiDailyRepository factKpiDailyRepository;
    private final FactKpiHourlyRepository factKpiHourlyRepository;
    private final FactKpiServerDailyRepository factKpiServerDailyRepository;
    private final DateDimensionRepository dateDimensionRepository;
    private final DailyRevenuePaymentBreakdownRepository dailyRevenuePaymentBreakdownRepository;
    private final StoreRepository storeRepository;
    private final CoffeeSalesHourlyRepository coffeeSalesHourlyRepository;
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
     * Total revenue TTC for one calendar day (scaled like {@link #getDailyReport(Long, LocalDate)}).
     * Empty when {@code date} is not present in the date dimension (same gate as GET /api/kpi/daily-report).
     */
    public Optional<Double> getDailyRevenueTtcForDate(Long storeId, LocalDate date) {
        if (dateDimensionRepository.findByDate(date).isEmpty()) {
            return Optional.empty();
        }
        DemoStoreDataSourceResolver.KpiDataContext kpiCtx = demoStoreDataSourceResolver.resolveKpiContext(storeId);
        Long dataStoreId = kpiCtx.dataStoreId();
        double mult = kpiCtx.revenueQuantityMultiplier();
        Optional<Double> raw = factKpiDailyRepository.findTotalRevenueTtcByStoreIdAndCalendarDate(dataStoreId, date);
        return Optional.of(scaledRevenue(raw.orElse(null), mult));
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

        double totalRevenue = scaledRevenue(dailyKpi.getTotalRevenueTtc(), mult);

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

        // Build and return daily report
        return DailyReportDTO.builder()
                .date(date.format(DATE_FORMATTER))
                .businessName(store.getName())
                .revenue(revenue)
                .hourlyData(hourlyData)
                .staffPerformance(staffPerformance)
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
        return DailyReportDTO.builder()
                .date(date.format(DATE_FORMATTER))
                .businessName(store.getName())
                .revenue(revenue)
                .hourlyData(hourlyData)
                .staffPerformance(Collections.emptyList())
                .build();
    }
    
    // -------------------------------------------------------------------------
    // Statistics range endpoint
    // -------------------------------------------------------------------------

    /**
     * Returns aggregated KPI statistics for a date range, powering the Statistiques dashboard.
     *
     * @param storeId     authenticated store
     * @param from        range start (null iff granularity == MONTHLY — computed internally)
     * @param to          range end   (null iff granularity == MONTHLY — computed internally)
     * @param granularity DAILY | WEEKLY | MONTHLY
     * @param limit       max number of best / worst products to return
     */
    public StatisticsResponseDTO getStatistics(Long storeId,
                                               LocalDate from, LocalDate to,
                                               Granularity granularity, int limit) {

        DemoStoreDataSourceResolver.KpiDataContext kpiCtx = demoStoreDataSourceResolver.resolveKpiContext(storeId);
        Long dataStoreId = kpiCtx.dataStoreId();
        double mult = kpiCtx.revenueQuantityMultiplier();

        // 1. Resolve effective date window
        final LocalDate effectiveFrom;
        final LocalDate effectiveTo;
        if (granularity == Granularity.MONTHLY) {
            effectiveTo   = LocalDate.now();
            effectiveFrom = YearMonth.now().minusMonths(11).atDay(1);
        } else {
            effectiveFrom = from;
            effectiveTo   = to;
        }

        // 2. Previous period window
        final LocalDate prevFrom;
        final LocalDate prevTo;
        if (granularity == Granularity.MONTHLY) {
            prevTo   = effectiveFrom.minusDays(1);
            prevFrom = YearMonth.from(prevTo).minusMonths(11).atDay(1);
        } else {
            long duration = ChronoUnit.DAYS.between(effectiveFrom, effectiveTo) + 1;
            prevTo   = effectiveFrom.minusDays(1);
            prevFrom = prevTo.minusDays(duration - 1);
        }

        // 3. Fetch daily KPI facts for both periods
        List<FactKpiDaily> currentFacts  = factKpiDailyRepository
                .findAllByStoreIdAndDateBetween(dataStoreId, effectiveFrom, effectiveTo);
        List<FactKpiDaily> previousFacts = factKpiDailyRepository
                .findAllByStoreIdAndDateBetween(dataStoreId, prevFrom, prevTo);

        // 4. Aggregate totals (current)
        double totalRevenue      = currentFacts.stream()
                .mapToDouble(r -> scaledRevenue(r.getTotalRevenueTtc(), mult)).sum();
        long   totalItems        = currentFacts.stream()
                .mapToLong(r -> r.getTotalItemsSold() != null ? (long) Math.round(r.getTotalItemsSold() * mult) : 0L).sum();
        long   totalTransactions = currentFacts.stream()
                .mapToLong(r -> r.getTransactions() != null ? r.getTransactions().longValue() : 0L).sum();

        // 5. Aggregate totals (previous)
        double prevRevenue      = previousFacts.stream()
                .mapToDouble(r -> scaledRevenue(r.getTotalRevenueTtc(), mult)).sum();
        long   prevItems        = previousFacts.stream()
                .mapToLong(r -> r.getTotalItemsSold() != null ? (long) Math.round(r.getTotalItemsSold() * mult) : 0L).sum();
        long   prevTransactions = previousFacts.stream()
                .mapToLong(r -> r.getTransactions() != null ? r.getTransactions().longValue() : 0L).sum();

        // 6. Average per period unit
        long periodCount     = countPeriodUnits(effectiveFrom, effectiveTo, granularity);
        long prevPeriodCount = countPeriodUnits(prevFrom, prevTo, granularity);
        double avgRevenue     = periodCount     > 0 ? totalRevenue / periodCount     : 0.0;
        double prevAvgRevenue = prevPeriodCount > 0 ? prevRevenue  / prevPeriodCount : 0.0;

        // 7. Build KPI card DTO
        StatisticsKpiCardDTO kpiCards = StatisticsKpiCardDTO.builder()
                .totalRevenueTtc(totalRevenue)
                .totalItemsSold(totalItems)
                .totalTransactions(totalTransactions)
                .avgRevenuePerPeriod(avgRevenue)
                .avgLabel(avgLabelFor(granularity))
                .revenuePct(pctChange(totalRevenue, prevRevenue))
                .itemsSoldPct(pctChange((double) totalItems, (double) prevItems))
                .transactionsPct(pctChange((double) totalTransactions, (double) prevTransactions))
                .avgRevenuePerPeriodPct(pctChange(avgRevenue, prevAvgRevenue))
                .build();

        // 8. Revenue evolution chart
        List<RevenueDataPointDTO> chart = buildRevenueChart(
                currentFacts, effectiveFrom, effectiveTo, granularity, mult);

        // 9. Product aggregates (current + previous)
        String storeCode = storeRepository.findById(dataStoreId)
                .map(Store::getCode)
                .orElseThrow(() -> new RuntimeException("Store not found: " + dataStoreId));
        List<ProductAggregate> currProducts = fetchProductAggregates(storeCode, effectiveFrom, effectiveTo, mult);
        List<ProductAggregate> prevProducts = fetchProductAggregates(storeCode, prevFrom, prevTo, mult);
        Map<String, Double> prevRevByProduct = prevProducts.stream()
                .collect(Collectors.toMap(ProductAggregate::productName, ProductAggregate::revenue));

        // 10. Best sales (descending by revenue)
        List<ProductAggregate> byRevDesc = currProducts.stream()
                .sorted(Comparator.comparingDouble(ProductAggregate::revenue).reversed())
                .collect(Collectors.toList());

        double maxRev = byRevDesc.isEmpty() ? 1.0 : byRevDesc.get(0).revenue();
        if (maxRev == 0.0) maxRev = 1.0;

        List<BestProductDTO> bestSales = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, byRevDesc.size()); i++) {
            ProductAggregate p = byRevDesc.get(i);
            bestSales.add(BestProductDTO.builder()
                    .rank(i + 1)
                    .productName(p.productName())
                    .quantitySold(p.quantity())
                    .revenue(p.revenue())
                    .performanceScore(p.revenue() / maxRev)
                    .build());
        }

        // 11. Worst sales (ascending by revenue)
        List<ProductAggregate> byRevAsc = currProducts.stream()
                .sorted(Comparator.comparingDouble(ProductAggregate::revenue))
                .collect(Collectors.toList());

        List<WorstProductDTO> worstSales = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, byRevAsc.size()); i++) {
            ProductAggregate p = byRevAsc.get(i);
            Double prevRev = prevRevByProduct.get(p.productName());
            worstSales.add(WorstProductDTO.builder()
                    .rank(i + 1)
                    .productName(p.productName())
                    .quantitySold(p.quantity())
                    .revenue(p.revenue())
                    .trendPct(prevRev != null ? pctChange(p.revenue(), prevRev) : null)
                    .build());
        }

        return StatisticsResponseDTO.builder()
                .kpiCards(kpiCards)
                .revenueChart(chart)
                .bestSales(bestSales)
                .worstSales(worstSales)
                .build();
    }

    // -- private helpers for getStatistics ------------------------------------

    /** Fetch and aggregate product data over a date range, applying the revenue multiplier. */
    private List<ProductAggregate> fetchProductAggregates(String storeCode,
                                                           LocalDate start, LocalDate end,
                                                           double mult) {
        List<Object[]> rows = coffeeSalesHourlyRepository
                .aggregateByStoreCodeAndDateRange(storeCode, start, end);
        List<ProductAggregate> result = new ArrayList<>();
        for (Object[] row : rows) {
            String name = (String) row[0];
            long   qty  = row[1] != null ? ((Number) row[1]).longValue()  : 0L;
            double rev  = row[2] != null ? scaledRevenue(((Number) row[2]).doubleValue(), mult) : 0.0;
            result.add(new ProductAggregate(name, qty, rev));
        }
        return result;
    }

    /** % change formula; returns null when previous == 0 (avoids division by zero). */
    private static Double pctChange(double current, double previous) {
        if (previous == 0.0) return null;
        return ((current - previous) / previous) * 100.0;
    }

    /** Number of period units (days / weeks / months) in [from, to]. */
    private long countPeriodUnits(LocalDate from, LocalDate to, Granularity granularity) {
        switch (granularity) {
            case DAILY:
                return ChronoUnit.DAYS.between(from, to) + 1;
            case WEEKLY: {
                // Rolling 7-day windows anchored on J-1 (yesterday); count windows overlapping [from, to]
                LocalDate anchor = LocalDate.now().minusDays(1);
                long count = 0;
                LocalDate windowEnd = anchor;
                while (!windowEnd.isBefore(from)) {
                    LocalDate windowStart = windowEnd.minusDays(6);
                    // window overlaps [from, to] if windowStart <= to && windowEnd >= from
                    if (!windowStart.isAfter(to) && !windowEnd.isBefore(from)) {
                        count++;
                    }
                    windowEnd = windowEnd.minusDays(7);
                }
                return Math.max(count, 1);
            }
            case MONTHLY:
                return 12L;
            default:
                return 1L;
        }
    }

    /** Human-readable label for the "average per period" KPI card. */
    private static String avgLabelFor(Granularity granularity) {
        switch (granularity) {
            case DAILY:   return "Jour";
            case WEEKLY:  return "Sem.";
            case MONTHLY: return "Mois";
            default:      return "Jour";
        }
    }

    /** Build the revenue chart data points according to the granularity. */
    private List<RevenueDataPointDTO> buildRevenueChart(List<FactKpiDaily> facts,
                                                         LocalDate from, LocalDate to,
                                                         Granularity granularity, double mult) {
        switch (granularity) {

            case DAILY: {
                Map<LocalDate, Double> revByDate = facts.stream()
                        .collect(Collectors.toMap(
                                r -> r.getDate().getDate(),
                                r -> scaledRevenue(r.getTotalRevenueTtc(), mult),
                                Double::sum));
                List<RevenueDataPointDTO> points = new ArrayList<>();
                LocalDate d = from;
                while (!d.isAfter(to)) {
                    points.add(RevenueDataPointDTO.builder()
                            .label(d.toString())
                            .revenue(revByDate.getOrDefault(d, 0.0))
                            .build());
                    d = d.plusDays(1);
                }
                return points;
            }

            case WEEKLY: {
                // Rolling 7-day windows anchored on J-1 (yesterday), going backward.
                // Collect windows that overlap [from, to], then reverse so W1 = oldest.
                LocalDate anchor = LocalDate.now().minusDays(1);

                // Index daily revenue by date for quick lookup
                Map<LocalDate, Double> revByDate = facts.stream()
                        .collect(Collectors.toMap(
                                r -> r.getDate().getDate(),
                                r -> scaledRevenue(r.getTotalRevenueTtc(), mult),
                                Double::sum));

                // Build windows (newest-first)
                List<double[]> windows = new ArrayList<>(); // [windowRevenue]
                List<LocalDate[]> windowBounds = new ArrayList<>();
                LocalDate windowEnd = anchor;
                while (!windowEnd.isBefore(from)) {
                    LocalDate windowStart = windowEnd.minusDays(6);
                    if (!windowStart.isAfter(to) && !windowEnd.isBefore(from)) {
                        LocalDate effectiveStart = windowStart.isBefore(from) ? from : windowStart;
                        LocalDate effectiveEnd   = windowEnd.isAfter(to)     ? to   : windowEnd;
                        double windowRev = 0.0;
                        LocalDate d = effectiveStart;
                        while (!d.isAfter(effectiveEnd)) {
                            windowRev += revByDate.getOrDefault(d, 0.0);
                            d = d.plusDays(1);
                        }
                        windows.add(new double[]{windowRev});
                        windowBounds.add(new LocalDate[]{windowStart, windowEnd});
                    }
                    windowEnd = windowEnd.minusDays(7);
                }

                // Reverse so that the oldest window is W1
                Collections.reverse(windows);
                List<RevenueDataPointDTO> points = new ArrayList<>();
                for (int i = 0; i < windows.size(); i++) {
                    points.add(RevenueDataPointDTO.builder()
                            .label("W" + (i + 1))
                            .revenue(windows.get(i)[0])
                            .build());
                }
                return points;
            }

            case MONTHLY: {
                Map<YearMonth, Double> revByMonth = new LinkedHashMap<>();
                for (FactKpiDaily row : facts) {
                    YearMonth ym = YearMonth.from(row.getDate().getDate());
                    revByMonth.merge(ym, scaledRevenue(row.getTotalRevenueTtc(), mult), Double::sum);
                }
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM yyyy", Locale.FRENCH);
                List<RevenueDataPointDTO> points = new ArrayList<>();
                YearMonth cur = YearMonth.from(from);
                YearMonth end = YearMonth.from(to);
                while (!cur.isAfter(end)) {
                    points.add(RevenueDataPointDTO.builder()
                            .label(cur.format(fmt))
                            .revenue(revByMonth.getOrDefault(cur, 0.0))
                            .build());
                    cur = cur.plusMonths(1);
                }
                return points;
            }

            default:
                return Collections.emptyList();
        }
    }

    /** Lightweight product aggregate used only inside getStatistics. */
    private record ProductAggregate(String productName, long quantity, double revenue) {}

    // -------------------------------------------------------------------------
    // Helper class for period ranges
    // -------------------------------------------------------------------------

}

