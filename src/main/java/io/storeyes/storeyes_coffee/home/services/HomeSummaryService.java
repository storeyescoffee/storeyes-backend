package io.storeyes.storeyes_coffee.home.services;

import io.storeyes.storeyes_coffee.alerts.entities.HumanJudgement;
import io.storeyes.storeyes_coffee.alerts.repositories.AlertRepository;
import io.storeyes.storeyes_coffee.home.dto.HomeSummaryResponse;
import io.storeyes.storeyes_coffee.kpi.services.KpiService;
import io.storeyes.storeyes_coffee.statistics.services.StatisticsService;
import io.storeyes.storeyes_coffee.store.services.DemoStoreDataSourceResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HomeSummaryService {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM");

    private final StatisticsService statisticsService;
    private final AlertRepository alertRepository;
    private final KpiService kpiService;
    private final DemoStoreDataSourceResolver demoStoreDataSourceResolver;

    /**
     * @param storeId      authenticated store (KPI / alerts source may be remapped for demo)
     * @param dateOverride optional {@code yyyy-MM-dd}; when omitted, display date uses the 21:00 UTC rollover
     */
    public HomeSummaryResponse getSummary(Long storeId, LocalDate dateOverride) {
        LocalDate displayDate = resolveDisplayDate(dateOverride);
        String monthKey = YearMonth.from(displayDate).format(MONTH_KEY);

        BigDecimal monthProfit = statisticsService.getMonthProfitMad(monthKey);

        Long alertsStoreId = demoStoreDataSourceResolver.resolveAlertsDataStoreId(storeId);
        long alertsCount = alertRepository.countProcessedHomeAlertsByDay(
                alertsStoreId,
                displayDate.atStartOfDay(),
                List.of(HumanJudgement.NEW, HumanJudgement.TRUE_POSITIVE));

        Optional<Double> dailyTtc = kpiService.getDailyRevenueTtcForDate(storeId, displayDate);

        return HomeSummaryResponse.builder()
                .monthProfit(monthProfit)
                .alertsCount(alertsCount)
                .dailyRevenueTtc(dailyTtc.orElse(null))
                .displayDate(displayDate.format(ISO_DATE))
                .monthKey(monthKey)
                .build();
    }

    /**
     * Business “display date”: before 21:00 UTC, counts as the previous calendar day; from 21:00 UTC inclusive,
     * the current UTC calendar day. When the client sends {@code date}, that value is used as-is.
     */
    public static LocalDate resolveDisplayDate(LocalDate override) {
        if (override != null) {
            return override;
        }
        ZonedDateTime utc = ZonedDateTime.now(ZoneOffset.UTC);
        LocalDate calendarDay = utc.toLocalDate();
        return utc.getHour() < 21 ? calendarDay.minusDays(1) : calendarDay;
    }
}
