package io.storeyes.storeyes_coffee.statistics.services;

import io.storeyes.storeyes_coffee.charges.entities.ChargeCategory;
import io.storeyes.storeyes_coffee.charges.entities.ChargePeriod;
import io.storeyes.storeyes_coffee.charges.entities.FixedCharge;
import io.storeyes.storeyes_coffee.charges.entities.VariableCharge;
import io.storeyes.storeyes_coffee.charges.repositories.FixedChargeRepository;
import io.storeyes.storeyes_coffee.charges.repositories.VariableChargeRepository;
import io.storeyes.storeyes_coffee.charges.utils.WeekCalculationUtils;
import io.storeyes.storeyes_coffee.kpi.entities.DateDimension;
import io.storeyes.storeyes_coffee.kpi.entities.FactKpiDaily;
import io.storeyes.storeyes_coffee.kpi.repositories.DateDimensionRepository;
import io.storeyes.storeyes_coffee.kpi.repositories.FactKpiDailyRepository;
import io.storeyes.storeyes_coffee.security.KeycloakTokenUtils;
import io.storeyes.storeyes_coffee.statistics.dto.*;
import io.storeyes.storeyes_coffee.store.services.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    /**
     * Fixed charge with the amount to use for the current period.
     * For week period, monthly charges are prorated (amount / weeksInMonth); weekly charges are full amount.
     */
    private static record FixedChargeWithAmount(FixedCharge charge, BigDecimal effectiveAmount) {}

    private final FactKpiDailyRepository factKpiDailyRepository;
    private final DateDimensionRepository dateDimensionRepository;
    private final FixedChargeRepository fixedChargeRepository;
    private final VariableChargeRepository variableChargeRepository;
    private final StoreService storeService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int SCALE = 2;

    /**
     * Get comprehensive statistics for a specific period
     */
    public StatisticsResponse getStatistics(String period, String date) {
        Long storeId = getStoreId();
        
        // Parse date based on period type
        LocalDate startDate;
        LocalDate endDate;
        LocalDate previousStartDate;
        LocalDate previousEndDate;
        
        switch (period.toLowerCase()) {
            case "day":
                LocalDate dayDate = LocalDate.parse(date, DATE_FORMATTER);
                startDate = dayDate;
                endDate = dayDate;
                previousStartDate = dayDate.minusDays(1);
                previousEndDate = previousStartDate;
                break;
            case "week":
                LocalDate weekDate = LocalDate.parse(date, DATE_FORMATTER);
                LocalDate monday = WeekCalculationUtils.getMondayOfWeek(weekDate);
                LocalDate sunday = WeekCalculationUtils.getSundayOfWeek(monday);
                startDate = monday;
                endDate = sunday;
                previousStartDate = monday.minusWeeks(1);
                previousEndDate = WeekCalculationUtils.getSundayOfWeek(previousStartDate);
                break;
            case "month":
                YearMonth yearMonth = YearMonth.parse(date, MONTH_FORMATTER);
                startDate = yearMonth.atDay(1);
                endDate = yearMonth.atEndOfMonth();
                YearMonth previousMonth = yearMonth.minusMonths(1);
                previousStartDate = previousMonth.atDay(1);
                previousEndDate = previousMonth.atEndOfMonth();
                break;
            default:
                throw new RuntimeException("Invalid period type. Must be 'day', 'week', or 'month'");
        }
        
        // Fetch revenue
        BigDecimal revenue = getRevenueForPeriod(storeId, startDate, endDate);
        BigDecimal previousRevenue = getRevenueForPeriod(storeId, previousStartDate, previousEndDate);
        
        // Fetch charges
        BigDecimal fixedChargesTotal = getFixedChargesForPeriod(storeId, period, date);
        BigDecimal variableChargesTotal = getVariableChargesForPeriod(storeId, startDate, endDate);
        BigDecimal totalCharges = fixedChargesTotal.add(variableChargesTotal);
        
        // Calculate KPIs
        BigDecimal profit = revenue.subtract(totalCharges);
        BigDecimal revenueEvolution = calculateRevenueEvolution(revenue, previousRevenue);
        BigDecimal chargesPercentage = calculatePercentage(totalCharges, revenue);
        BigDecimal profitPercentage = calculatePercentage(profit, revenue);
        String chargesStatus = calculateChargesStatus(chargesPercentage);
        String profitStatus = calculateProfitStatus(profitPercentage);
        
        KpiDTO kpi = KpiDTO.builder()
                .revenue(revenue)
                .charges(totalCharges)
                .profit(profit)
                .revenueEvolution(revenueEvolution)
                .chargesPercentage(chargesPercentage)
                .profitPercentage(profitPercentage)
                .chargesStatus(chargesStatus)
                .profitStatus(profitStatus)
                .build();
        
        // Generate chart data
        List<ChartDataDTO> chartData = generateChartData(storeId, period, date);
        
        // Get charges breakdown
        ChargesDTO charges = getChargesBreakdown(storeId, period, date, startDate, endDate, totalCharges, revenue);
        
        return StatisticsResponse.builder()
                .period(period)
                .kpi(kpi)
                .chartData(chartData)
                .charges(charges)
                .build();
    }

    /**
     * Get detailed charges breakdown
     */
    public ChargesDetailResponse getChargesDetail(String period, String month, String week) {
        Long storeId = getStoreId();
        
        if (!period.equalsIgnoreCase("week") && !period.equalsIgnoreCase("month")) {
            throw new RuntimeException("Period must be 'week' or 'month'");
        }
        
        LocalDate startDate;
        LocalDate endDate;
        
        if (period.equalsIgnoreCase("week")) {
            if (week == null || week.isEmpty()) {
                throw new RuntimeException("Week parameter is required for week period");
            }
            LocalDate weekDate = LocalDate.parse(week, DATE_FORMATTER);
            LocalDate monday = WeekCalculationUtils.getMondayOfWeek(weekDate);
            LocalDate sunday = WeekCalculationUtils.getSundayOfWeek(monday);
            startDate = monday;
            endDate = sunday;
        } else {
            YearMonth yearMonth = YearMonth.parse(month, MONTH_FORMATTER);
            startDate = yearMonth.atDay(1);
            endDate = yearMonth.atEndOfMonth();
        }
        
        // Fetch revenue
        BigDecimal revenue = getRevenueForPeriod(storeId, startDate, endDate);
        
        // Fetch fixed charges with effective amounts (prorated for week period)
        String dateParam = period.equalsIgnoreCase("week") ? week : month;
        List<FixedChargeWithAmount> fixedWithAmounts = getFixedChargesWithEffectiveAmounts(storeId, period, dateParam, week);
        BigDecimal totalFixedCharges = fixedWithAmounts.stream()
                .map(FixedChargeWithAmount::effectiveAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<VariableCharge> variableCharges = variableChargeRepository.findByStoreIdAndDateRange(storeId, startDate, endDate);
        BigDecimal totalVariableCharges = variableCharges.stream()
                .map(VariableCharge::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCharges = totalFixedCharges.add(totalVariableCharges);

        List<ChargeItemDTO> fixedChargesDTO = fixedWithAmounts.stream()
                .map(w -> toChargeItemDTO(w.charge(), w.effectiveAmount(), totalCharges, revenue))
                .collect(Collectors.toList());
        
        // Build variable charges DTOs
        List<ChargeItemDTO> variableChargesDTO = variableCharges.stream()
                .map(charge -> toChargeItemDTO(charge, totalCharges, revenue))
                .collect(Collectors.toList());
        
        // Build statistics
        ChargesStatisticsDTO statistics = ChargesStatisticsDTO.builder()
                .totalCharges(totalCharges)
                .totalFixedCharges(totalFixedCharges)
                .totalVariableCharges(totalVariableCharges)
                .itemCount(fixedWithAmounts.size() + variableCharges.size())
                .percentageOfAllCharges(calculatePercentage(totalVariableCharges, totalCharges))
                .caPercentage(calculatePercentage(totalCharges, revenue))
                .revenue(revenue)
                .build();
        
        return ChargesDetailResponse.builder()
                .period(period)
                .statistics(statistics)
                .fixedCharges(fixedChargesDTO)
                .variableCharges(variableChargesDTO)
                .build();
    }

    /**
     * Get revenue for a date range
     */
    private BigDecimal getRevenueForPeriod(Long storeId, LocalDate startDate, LocalDate endDate) {
        List<DateDimension> dates = new ArrayList<>();
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            dateDimensionRepository.findByDate(current).ifPresent(dates::add);
            current = current.plusDays(1);
        }
        
        BigDecimal total = BigDecimal.ZERO;
        for (DateDimension date : dates) {
            Optional<FactKpiDaily> daily = factKpiDailyRepository.findByStoreIdAndDate(storeId, date);
            if (daily.isPresent()) {
                total = total.add(BigDecimal.valueOf(daily.get().getTotalRevenueTtc()));
            }
        }
        
        return total.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Get fixed charges total for a period (uses effective amounts: prorated for week/day).
     */
    private BigDecimal getFixedChargesForPeriod(Long storeId, String period, String date) {
        List<FixedChargeWithAmount> withAmounts = getFixedChargesWithEffectiveAmounts(storeId, period, date, null);
        return withAmounts.stream()
                .map(FixedChargeWithAmount::effectiveAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Get fixed charges with effective amounts for the period.
     * For week: monthly charges prorated by weeks in month; weekly charges for that week at full amount.
     * For month: all fixed for month at full amount.
     * For day: monthly prorated by days in month; weekly for that week prorated by 7.
     */
    private List<FixedChargeWithAmount> getFixedChargesWithEffectiveAmounts(Long storeId, String period, String date, String week) {
        if (period.equalsIgnoreCase("day")) {
            LocalDate dayDate = LocalDate.parse(date, DATE_FORMATTER);
            String monthKey = dayDate.format(MONTH_FORMATTER);
            YearMonth yearMonth = YearMonth.parse(monthKey, MONTH_FORMATTER);
            int daysInMonth = yearMonth.lengthOfMonth();
            LocalDate monday = WeekCalculationUtils.getMondayOfWeek(dayDate);
            String weekKey = WeekCalculationUtils.generateWeekKey(monday);

            List<FixedChargeWithAmount> result = new ArrayList<>();
            List<FixedCharge> monthCharges = fixedChargeRepository.findByStoreIdAndMonthKey(storeId, monthKey);
            for (FixedCharge c : monthCharges) {
                if (c.getPeriod() == ChargePeriod.MONTH) {
                    BigDecimal effective = daysInMonth > 0
                            ? c.getAmount().divide(BigDecimal.valueOf(daysInMonth), SCALE, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    result.add(new FixedChargeWithAmount(c, effective));
                }
            }
            List<FixedCharge> weekCharges = fixedChargeRepository.findByStoreIdAndMonthKeyAndWeekKey(storeId, monthKey, weekKey);
            for (FixedCharge c : weekCharges) {
                if (c.getPeriod() == ChargePeriod.WEEK) {
                    BigDecimal effective = c.getAmount().divide(BigDecimal.valueOf(7), SCALE, RoundingMode.HALF_UP);
                    result.add(new FixedChargeWithAmount(c, effective));
                }
            }
            return result;
        } else if (period.equalsIgnoreCase("week")) {
            String weekKey = (week != null && !week.isEmpty()) ? week : date;
            LocalDate weekDate = LocalDate.parse(weekKey, DATE_FORMATTER);
            LocalDate monday = WeekCalculationUtils.getMondayOfWeek(weekDate);
            String monthKey = monday.format(MONTH_FORMATTER);
            String mondayWeekKey = WeekCalculationUtils.generateWeekKey(monday);
            int weeksCount = WeekCalculationUtils.getWeeksCountInMonth(monthKey);

            List<FixedChargeWithAmount> result = new ArrayList<>();
            List<FixedCharge> monthCharges = fixedChargeRepository.findByStoreIdAndMonthKey(storeId, monthKey);
            for (FixedCharge c : monthCharges) {
                if (c.getPeriod() == ChargePeriod.MONTH) {
                    BigDecimal effective = c.getAmount().divide(BigDecimal.valueOf(weeksCount), SCALE, RoundingMode.HALF_UP);
                    result.add(new FixedChargeWithAmount(c, effective));
                }
            }
            List<FixedCharge> weekCharges = fixedChargeRepository.findByStoreIdAndMonthKeyAndWeekKey(storeId, monthKey, mondayWeekKey);
            for (FixedCharge c : weekCharges) {
                result.add(new FixedChargeWithAmount(c, c.getAmount()));
            }
            return result;
        } else {
            // month
            List<FixedCharge> charges = fixedChargeRepository.findByStoreIdAndMonthKey(storeId, date);
            return charges.stream()
                    .map(c -> new FixedChargeWithAmount(c, c.getAmount()))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Get variable charges total for a date range
     */
    private BigDecimal getVariableChargesForPeriod(Long storeId, LocalDate startDate, LocalDate endDate) {
        List<VariableCharge> charges = variableChargeRepository.findByStoreIdAndDateRange(storeId, startDate, endDate);
        return charges.stream()
                .map(VariableCharge::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Generate chart data based on period type
     */
    private List<ChartDataDTO> generateChartData(Long storeId, String period, String date) {
        switch (period.toLowerCase()) {
            case "day":
                return generateDayChartData(storeId, date);
            case "week":
                return generateWeekChartData(storeId, date);
            case "month":
                return generateMonthChartData(storeId, date);
            default:
                return Collections.emptyList();
        }
    }

    /**
     * Generate chart data for day period (shows week: Mon-Sun)
     */
    private List<ChartDataDTO> generateDayChartData(Long storeId, String date) {
        LocalDate dayDate = LocalDate.parse(date, DATE_FORMATTER);
        LocalDate monday = WeekCalculationUtils.getMondayOfWeek(dayDate);
        String monthKey = monday.format(MONTH_FORMATTER);
        
        // Get monthly fixed charges once and calculate daily average
        List<FixedCharge> monthlyCharges = fixedChargeRepository.findByStoreIdAndMonthKey(storeId, monthKey);
        BigDecimal monthlyFixedTotal = monthlyCharges.stream()
                .filter(c -> c.getPeriod() == ChargePeriod.MONTH)
                .map(FixedCharge::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Calculate daily average for monthly charges
        BigDecimal dailyFixedAverage = BigDecimal.ZERO;
        if (monthlyFixedTotal.compareTo(BigDecimal.ZERO) > 0) {
            YearMonth yearMonth = YearMonth.parse(monthKey, MONTH_FORMATTER);
            int daysInMonth = yearMonth.lengthOfMonth();
            if (daysInMonth > 0) {
                dailyFixedAverage = monthlyFixedTotal.divide(
                        BigDecimal.valueOf(daysInMonth), SCALE, RoundingMode.HALF_UP);
            }
        }
        
        // Get weekly charges for the week
        List<FixedCharge> weeklyCharges = fixedChargeRepository.findByStoreIdAndMonthKeyAndWeekKey(
                storeId, monthKey, WeekCalculationUtils.generateWeekKey(monday));
        BigDecimal weeklyFixedTotal = weeklyCharges.stream()
                .filter(c -> c.getPeriod() == ChargePeriod.WEEK)
                .map(FixedCharge::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal dailyWeeklyAverage = BigDecimal.ZERO;
        if (weeklyFixedTotal.compareTo(BigDecimal.ZERO) > 0) {
            dailyWeeklyAverage = weeklyFixedTotal.divide(
                    BigDecimal.valueOf(7), SCALE, RoundingMode.HALF_UP);
        }
        
        BigDecimal dailyFixedCharges = dailyFixedAverage.add(dailyWeeklyAverage);
        
        List<ChartDataDTO> chartData = new ArrayList<>();
        String[] dayLabels = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        
        for (int i = 0; i < 7; i++) {
            LocalDate currentDate = monday.plusDays(i);
            BigDecimal revenue = getRevenueForPeriod(storeId, currentDate, currentDate);
            BigDecimal variableCharges = getVariableChargesForPeriod(storeId, currentDate, currentDate);
            BigDecimal charges = dailyFixedCharges.add(variableCharges);
            BigDecimal profit = revenue.subtract(charges);
            
            chartData.add(ChartDataDTO.builder()
                    .period(dayLabels[i])
                    .revenue(revenue)
                    .charges(charges)
                    .profit(profit)
                    .build());
        }
        
        return chartData;
    }

    /**
     * Generate chart data for week period (shows weeks in month: W1, W2, W3, W4).
     * Fixed charges per week = prorated monthly + full weekly for that week.
     */
    private List<ChartDataDTO> generateWeekChartData(Long storeId, String weekKey) {
        LocalDate weekDate = LocalDate.parse(weekKey, DATE_FORMATTER);
        LocalDate monday = WeekCalculationUtils.getMondayOfWeek(weekDate);
        String monthKey = monday.format(MONTH_FORMATTER);

        List<WeekCalculationUtils.WeekInfo> weeks = WeekCalculationUtils.getWeeksBelongingToMonth(monthKey);

        List<ChartDataDTO> chartData = new ArrayList<>();
        int weekNumber = 1;

        for (WeekCalculationUtils.WeekInfo week : weeks) {
            LocalDate weekMonday = week.getStartDate();
            LocalDate weekSunday = week.getEndDate();

            BigDecimal revenue = getRevenueForPeriod(storeId, weekMonday, weekSunday);
            List<FixedChargeWithAmount> fixedWithAmounts = getFixedChargesWithEffectiveAmounts(storeId, "week", null, week.getWeekKey());
            BigDecimal fixedCharges = fixedWithAmounts.stream()
                    .map(FixedChargeWithAmount::effectiveAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(SCALE, RoundingMode.HALF_UP);
            BigDecimal variableCharges = getVariableChargesForPeriod(storeId, weekMonday, weekSunday);
            BigDecimal charges = fixedCharges.add(variableCharges);
            BigDecimal profit = revenue.subtract(charges);

            chartData.add(ChartDataDTO.builder()
                    .period("W" + weekNumber)
                    .revenue(revenue)
                    .charges(charges)
                    .profit(profit)
                    .build());

            weekNumber++;
        }

        return chartData;
    }

    /**
     * Generate chart data for month period (shows last 4 months)
     */
    private List<ChartDataDTO> generateMonthChartData(Long storeId, String monthKey) {
        YearMonth currentMonth = YearMonth.parse(monthKey, MONTH_FORMATTER);
        
        List<ChartDataDTO> chartData = new ArrayList<>();
        
        // Get last 4 months including current
        for (int i = 3; i >= 0; i--) {
            YearMonth month = currentMonth.minusMonths(i);
            LocalDate monthStart = month.atDay(1);
            LocalDate monthEnd = month.atEndOfMonth();
            
            BigDecimal revenue = getRevenueForPeriod(storeId, monthStart, monthEnd);
            BigDecimal fixedCharges = getFixedChargesForPeriod(storeId, "month", month.format(MONTH_FORMATTER));
            BigDecimal variableCharges = getVariableChargesForPeriod(storeId, monthStart, monthEnd);
            BigDecimal charges = fixedCharges.add(variableCharges);
            BigDecimal profit = revenue.subtract(charges);
            
            String monthLabel = month.format(DateTimeFormatter.ofPattern("MMM"));
            
            chartData.add(ChartDataDTO.builder()
                    .period(monthLabel)
                    .revenue(revenue)
                    .charges(charges)
                    .profit(profit)
                    .build());
        }
        
        return chartData;
    }

    /**
     * Get charges breakdown (uses effective amounts for fixed charges in week/day).
     */
    private ChargesDTO getChargesBreakdown(Long storeId, String period, String date,
                                          LocalDate startDate, LocalDate endDate,
                                          BigDecimal totalCharges, BigDecimal revenue) {
        List<FixedChargeWithAmount> fixedWithAmounts = getFixedChargesWithEffectiveAmounts(storeId, period, date, null);
        List<ChargeItemDTO> fixedDTOs = fixedWithAmounts.stream()
                .map(w -> toChargeItemDTO(w.charge(), w.effectiveAmount(), totalCharges, revenue))
                .collect(Collectors.toList());

        List<VariableCharge> variableCharges = variableChargeRepository.findByStoreIdAndDateRange(storeId, startDate, endDate);
        List<ChargeItemDTO> variableDTOs = variableCharges.stream()
                .map(charge -> toChargeItemDTO(charge, totalCharges, revenue))
                .collect(Collectors.toList());

        return ChargesDTO.builder()
                .fixed(fixedDTOs)
                .variable(variableDTOs)
                .build();
    }

    /**
     * Convert FixedCharge to ChargeItemDTO using effective amount (for prorated week/day).
     */
    private ChargeItemDTO toChargeItemDTO(FixedCharge charge, BigDecimal effectiveAmount, BigDecimal totalCharges, BigDecimal revenue) {
        String name = getChargeName(charge);
        BigDecimal percentageOfCharges = calculatePercentage(effectiveAmount, totalCharges);
        BigDecimal percentageOfRevenue = calculatePercentage(effectiveAmount, revenue);
        String status = calculateChargeStatus(percentageOfRevenue);

        return ChargeItemDTO.builder()
                .id(charge.getId())
                .name(name)
                .amount(effectiveAmount)
                .percentageOfCharges(percentageOfCharges)
                .percentageOfRevenue(percentageOfRevenue)
                .category("fixed")
                .status(status)
                .build();
    }

    /**
     * Convert VariableCharge to ChargeItemDTO
     */
    private ChargeItemDTO toChargeItemDTO(VariableCharge charge, BigDecimal totalCharges, BigDecimal revenue) {
        BigDecimal amount = charge.getAmount();
        BigDecimal percentageOfCharges = calculatePercentage(amount, totalCharges);
        BigDecimal percentageOfRevenue = calculatePercentage(amount, revenue);
        String status = calculateChargeStatus(percentageOfRevenue);
        
        return ChargeItemDTO.builder()
                .id(charge.getId())
                .name(charge.getName())
                .amount(amount)
                .percentageOfCharges(percentageOfCharges)
                .percentageOfRevenue(percentageOfRevenue)
                .category("variable")
                .status(status)
                .date(charge.getDate().format(DATE_FORMATTER))
                .supplier(charge.getSupplier())
                .build();
    }

    /**
     * Get charge name from FixedCharge
     */
    private String getChargeName(FixedCharge charge) {
        ChargeCategory category = charge.getCategory();
        switch (category) {
            case PERSONNEL:
                return "Personnel";
            case WATER:
                return "Water";
            case ELECTRICITY:
                return "Electricity";
            case WIFI:
                return "WiFi";
            default:
                return category.name();
        }
    }

    /**
     * Calculate percentage
     */
    private BigDecimal calculatePercentage(BigDecimal part, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return part.divide(total, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calculate revenue evolution percentage
     */
    private BigDecimal calculateRevenueEvolution(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return current.subtract(previous)
                .divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calculate charges status
     */
    private String calculateChargesStatus(BigDecimal percentage) {
        if (percentage.compareTo(BigDecimal.valueOf(75)) > 0) {
            return "critical";
        } else if (percentage.compareTo(BigDecimal.valueOf(66)) > 0) {
            return "medium";
        } else {
            return "good";
        }
    }

    /**
     * Calculate profit status
     */
    private String calculateProfitStatus(BigDecimal percentage) {
        if (percentage.compareTo(BigDecimal.valueOf(33)) >= 0) {
            return "good";
        } else if (percentage.compareTo(BigDecimal.valueOf(15)) >= 0) {
            return "medium";
        } else {
            return "critical";
        }
    }

    /**
     * Calculate charge item status (based on percentage of revenue)
     */
    private String calculateChargeStatus(BigDecimal percentageOfRevenue) {
        // Simple heuristic: if charge is > 20% of revenue, it's critical
        if (percentageOfRevenue.compareTo(BigDecimal.valueOf(20)) > 0) {
            return "critical";
        } else if (percentageOfRevenue.compareTo(BigDecimal.valueOf(10)) > 0) {
            return "medium";
        } else {
            return "good";
        }
    }

    /**
     * Get store ID from authenticated user
     */
    private Long getStoreId() {
        String userId = KeycloakTokenUtils.getUserId();
        if (userId == null) {
            throw new RuntimeException("User is not authenticated");
        }
        return storeService.getStoreByOwnerId(userId).getId();
    }
}
