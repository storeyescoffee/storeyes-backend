package io.storeyes.storeyes_coffee.charges.utils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for week calculations based on ISO 8601 standard (Monday-Sunday weeks).
 * 
 * Key rules:
 * - Weeks always start on Monday and end on Sunday
 * - Weeks can span 2 months
 * - A week belongs to the month where its Monday falls
 * - Week key format: "YYYY-MM-DD" (Monday date)
 */
public class WeekCalculationUtils {

    private static final DateTimeFormatter MONTH_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter WEEK_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Get Monday of the week containing the given date
     */
    public static LocalDate getMondayOfWeek(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        int daysToSubtract = dayOfWeek == DayOfWeek.SUNDAY ? 6 : dayOfWeek.getValue() - 1;
        return date.minusDays(daysToSubtract);
    }

    /**
     * Get Sunday of the week containing the given date
     */
    public static LocalDate getSundayOfWeek(LocalDate date) {
        LocalDate monday = getMondayOfWeek(date);
        return monday.plusDays(6);
    }

    /**
     * Generate week key from Monday date
     */
    public static String generateWeekKey(LocalDate mondayDate) {
        return mondayDate.format(WEEK_KEY_FORMATTER);
    }

    /**
     * Parse week key to Monday date
     */
    public static LocalDate parseWeekKey(String weekKey) {
        return LocalDate.parse(weekKey, WEEK_KEY_FORMATTER);
    }

    /**
     * Get month key from date (format: "YYYY-MM")
     */
    public static String getMonthKey(LocalDate date) {
        return date.format(MONTH_KEY_FORMATTER);
    }

    /**
     * Get month key for a week (the month where Monday falls)
     */
    public static String getMonthKeyForWeek(String weekKey) {
        LocalDate monday = parseWeekKey(weekKey);
        return getMonthKey(monday);
    }

    /**
     * Validate that a date is a Monday
     */
    public static boolean isMonday(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.MONDAY;
    }

    /**
     * Represents a week with its metadata
     */
    public static class WeekInfo {
        private final String weekKey;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final boolean belongsToMonth;
        private final String monthKey;

        public WeekInfo(String weekKey, LocalDate startDate, LocalDate endDate, boolean belongsToMonth, String monthKey) {
            this.weekKey = weekKey;
            this.startDate = startDate;
            this.endDate = endDate;
            this.belongsToMonth = belongsToMonth;
            this.monthKey = monthKey;
        }

        public String getWeekKey() {
            return weekKey;
        }

        public LocalDate getStartDate() {
            return startDate;
        }

        public LocalDate getEndDate() {
            return endDate;
        }

        public boolean belongsToMonth() {
            return belongsToMonth;
        }

        public String getMonthKey() {
            return monthKey;
        }
    }

    /**
     * Get all weeks that overlap with a month
     * Returns weeks that either start in the month or end in the month
     * 
     * @param monthKey Format: "YYYY-MM"
     * @return List of WeekInfo objects
     */
    public static List<WeekInfo> getWeeksForMonth(String monthKey) {
        YearMonth yearMonth = YearMonth.parse(monthKey, MONTH_KEY_FORMATTER);
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();

        // Get Monday of the first week that overlaps with the month
        LocalDate firstMonday = getMondayOfWeek(monthStart);
        
        // If the first Monday is before month start, start from the previous week
        if (firstMonday.isBefore(monthStart)) {
            // Include the week that starts before the month if it overlaps
            firstMonday = firstMonday.minusWeeks(1);
        }

        List<WeekInfo> weeks = new ArrayList<>();
        LocalDate currentMonday = firstMonday;

        // Iterate through weeks until we pass the month end
        while (currentMonday.isBefore(monthEnd) || currentMonday.isEqual(monthEnd) || getSundayOfWeek(currentMonday).isAfter(monthStart)) {
            LocalDate sunday = getSundayOfWeek(currentMonday);
            
            // Check if this week overlaps with the month
            if (!sunday.isBefore(monthStart) && !currentMonday.isAfter(monthEnd)) {
                String weekKey = generateWeekKey(currentMonday);
                String weekMonthKey = getMonthKey(currentMonday); // Month where Monday falls
                boolean belongsToMonth = weekMonthKey.equals(monthKey);
                
                weeks.add(new WeekInfo(weekKey, currentMonday, sunday, belongsToMonth, weekMonthKey));
            }
            
            currentMonday = currentMonday.plusWeeks(1);
            
            // Safety check to avoid infinite loop
            if (currentMonday.isAfter(monthEnd.plusWeeks(1))) {
                break;
            }
        }

        return weeks;
    }

    /**
     * Get all weeks that belong to a month (where Monday is in the month)
     * 
     * @param monthKey Format: "YYYY-MM"
     * @return List of WeekInfo objects that belong to the month
     */
    public static List<WeekInfo> getWeeksBelongingToMonth(String monthKey) {
        List<WeekInfo> allWeeks = getWeeksForMonth(monthKey);
        List<WeekInfo> weeksInMonth = new ArrayList<>();
        
        for (WeekInfo week : allWeeks) {
            if (week.belongsToMonth()) {
                weeksInMonth.add(week);
            }
        }
        
        return weeksInMonth;
    }

    /**
     * Get the number of weeks that overlap with a month.
     * Used to prorate monthly fixed charges when returning week-period statistics.
     *
     * @param monthKey Format: "YYYY-MM"
     * @return Number of weeks (typically 4 or 5)
     */
    public static int getWeeksCountInMonth(String monthKey) {
        List<WeekInfo> weeks = getWeeksForMonth(monthKey);
        return Math.max(weeks.size(), 1);
    }

    /**
     * Distribute monthly salary across weeks that belong to the month
     * All weeks are treated as full weeks (7 days each), so salary is distributed equally
     * 
     * @param monthSalary Total monthly salary
     * @param monthKey Format: "YYYY-MM"
     * @return Map of week key to salary amount
     */
    public static Map<String, java.math.BigDecimal> distributeMonthlySalary(
            java.math.BigDecimal monthSalary,
            String monthKey) {
        
        List<WeekInfo> weeksInMonth = getWeeksBelongingToMonth(monthKey);
        
        if (weeksInMonth.isEmpty()) {
            return new HashMap<>();
        }
        
        // Distribute salary equally across all weeks in the month
        java.math.BigDecimal salaryPerWeek = monthSalary.divide(
                java.math.BigDecimal.valueOf(weeksInMonth.size()),
                4, // precision scale
                java.math.RoundingMode.HALF_UP
        );
        
        Map<String, java.math.BigDecimal> weekSalaries = new HashMap<>();
        for (WeekInfo week : weeksInMonth) {
            weekSalaries.put(week.getWeekKey(), salaryPerWeek);
        }
        
        // Handle rounding differences by adjusting the first week
        java.math.BigDecimal total = weekSalaries.values().stream()
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal difference = monthSalary.subtract(total);
        
        if (weeksInMonth.size() > 0 && difference.abs().compareTo(java.math.BigDecimal.valueOf(0.01)) > 0) {
            String firstWeekKey = weeksInMonth.get(0).getWeekKey();
            java.math.BigDecimal currentAmount = weekSalaries.get(firstWeekKey);
            weekSalaries.put(firstWeekKey, currentAmount.add(difference));
        }
        
        return weekSalaries;
    }

    /**
     * Check if a week key is valid (must be a Monday)
     */
    public static boolean isValidWeekKey(String weekKey) {
        try {
            LocalDate date = parseWeekKey(weekKey);
            return isMonday(date);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if a week overlaps with a month
     */
    public static boolean weekOverlapsWithMonth(String weekKey, String monthKey) {
        try {
            LocalDate monday = parseWeekKey(weekKey);
            LocalDate sunday = getSundayOfWeek(monday);
            
            YearMonth yearMonth = YearMonth.parse(monthKey, MONTH_KEY_FORMATTER);
            LocalDate monthStart = yearMonth.atDay(1);
            LocalDate monthEnd = yearMonth.atEndOfMonth();
            
            return !sunday.isBefore(monthStart) && !monday.isAfter(monthEnd);
        } catch (Exception e) {
            return false;
        }
    }
}
