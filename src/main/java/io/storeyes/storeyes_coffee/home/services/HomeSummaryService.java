package io.storeyes.storeyes_coffee.home.services;

import io.storeyes.storeyes_coffee.alerts.entities.AlertType;
import io.storeyes.storeyes_coffee.alerts.entities.HumanJudgement;
import io.storeyes.storeyes_coffee.alerts.repositories.AlertRepository;
import io.storeyes.storeyes_coffee.home.dto.HomeSummaryResponse;
import io.storeyes.storeyes_coffee.kpi.services.KpiService;
import io.storeyes.storeyes_coffee.statistics.services.StatisticsService;
import io.storeyes.storeyes_coffee.store.repositories.StoreRepository;
import io.storeyes.storeyes_coffee.store.services.DemoStoreDataSourceResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final StoreRepository storeRepository;

    /**
     * @param storeId      authenticated store (KPI / alerts source may be remapped for demo)
     * @param dateOverride optional {@code yyyy-MM-dd}; when omitted, display date uses the 21:00 UTC rollover
     */
    public HomeSummaryResponse getSummary(Long storeId, LocalDate dateOverride) {
        LocalDate displayDate = resolveDisplayDate(dateOverride);
        String monthKey = YearMonth.from(displayDate).format(MONTH_KEY);

        BigDecimal monthProfit = statisticsService.getMonthProfitMad(monthKey);

        DemoStoreDataSourceResolver.AlertsDataContext alertsCtx =
                demoStoreDataSourceResolver.resolveAlertsDataContext(storeId);
        // For demo stores, use the fixed alertDate from the mapping instead of the display date
        LocalDate alertsQueryDate = alertsCtx.alertDate() != null ? alertsCtx.alertDate() : displayDate;

        // Per-store alert-type visibility: count only enabled types. Flags are read from the
        // selected store (not the demo data store), where the configuration lives.
        var store = storeRepository.findById(storeId).orElse(null);
        boolean notTappedEnabled = store == null || store.isNotTappedAlertsEnabled();
        boolean returnEnabled = store == null || store.isReturnAlertsEnabled();

        // Alerts activation: locked until alerts_activation_date (default installation + 3 weeks).
        // Null activation date (legacy stores) counts as active.
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime activationDate = store != null ? store.getAlertsActivationDate() : null;
        boolean alertsActive = activationDate == null || !now.isBefore(activationDate);
        int activationProgress;
        if (alertsActive) {
            activationProgress = 100;
        } else {
            // Progress is measured from the store's real-world installation date, not
            // created_at, since the DB row can be created before/after the actual install.
            LocalDateTime start = store.getInstallationDate() != null
                    ? store.getInstallationDate()
                    : activationDate.minusWeeks(3);
            long totalSeconds = Duration.between(start, activationDate).getSeconds();
            long elapsedSeconds = Duration.between(start, now).getSeconds();
            activationProgress = totalSeconds <= 0
                    ? 99
                    : (int) Math.min(99, Math.max(0, elapsedSeconds * 100 / totalSeconds));
        }

        long alertsCount;
        if (!alertsActive || (!notTappedEnabled && !returnEnabled)) {
            alertsCount = 0;
        } else if (notTappedEnabled && returnEnabled) {
            alertsCount = alertRepository.countProcessedHomeAlertsByDay(
                    alertsCtx.dataStoreId(),
                    alertsQueryDate.atStartOfDay(),
                    List.of(HumanJudgement.NEW, HumanJudgement.TRUE_POSITIVE));
        } else {
            List<AlertType> enabledTypes = notTappedEnabled
                    ? List.of(AlertType.NOT_TAPPED)
                    : List.of(AlertType.RETURN);
            alertsCount = alertRepository.countProcessedHomeAlertsByDayAndTypes(
                    alertsCtx.dataStoreId(),
                    alertsQueryDate.atStartOfDay(),
                    List.of(HumanJudgement.NEW, HumanJudgement.TRUE_POSITIVE),
                    enabledTypes,
                    notTappedEnabled); // null alertType counts as NOT_TAPPED
        }

        Optional<Double> dailyTtc = kpiService.getDailyRevenueTtcForDate(storeId, displayDate);

        return HomeSummaryResponse.builder()
                .monthProfit(monthProfit)
                .alertsCount(alertsCount)
                .dailyRevenueTtc(dailyTtc.orElse(null))
                .displayDate(displayDate.format(ISO_DATE))
                .monthKey(monthKey)
                .alertsActive(alertsActive)
                .alertsActivationProgress(activationProgress)
                .alertsActivationDate(activationDate != null
                        ? activationDate.toLocalDate().format(ISO_DATE)
                        : null)
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
