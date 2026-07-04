package io.storeyes.storeyes_coffee.alerts.services;

import io.storeyes.storeyes_coffee.alerts.dto.AlertDTO;
import io.storeyes.storeyes_coffee.alerts.dto.AlertDetailsDTO;
import io.storeyes.storeyes_coffee.alerts.dto.AlertSettingsDTO;
import io.storeyes.storeyes_coffee.alerts.dto.AlertSummaryDTO;
import io.storeyes.storeyes_coffee.alerts.dto.CreateAlertRequest;
import io.storeyes.storeyes_coffee.alerts.entities.Alert;
import io.storeyes.storeyes_coffee.alerts.entities.HumanJudgement;
import io.storeyes.storeyes_coffee.alerts.mappers.AlertMapper;
import io.storeyes.storeyes_coffee.alerts.repositories.AlertRepository;
import io.storeyes.storeyes_coffee.security.CurrentStoreContext;
import io.storeyes.storeyes_coffee.store.entities.Store;
import io.storeyes.storeyes_coffee.store.repositories.StoreRepository;
import io.storeyes.storeyes_coffee.store.services.DemoStoreDataSourceResolver;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlertService {
    
    private final AlertRepository alertRepository;
    private final AlertMapper alertMapper;
    private final StoreRepository storeRepository;
    private final DemoStoreDataSourceResolver demoStoreDataSourceResolver;
    
    /**
     * Create a new alert
     * If an alert with the same alertDate and alertType already exists, ignores it without creating a duplicate
     * If alertType is null, defaults to NOT_TAPPED
     */
    public void createAlert(CreateAlertRequest request) {
        // Get store by code
        var store = storeRepository.findByCode(request.getStoreCode())
                .orElseThrow(() -> new RuntimeException("Store not found with code: " + request.getStoreCode()));
        
        // Determine alertType (default to NOT_TAPPED if null)
        io.storeyes.storeyes_coffee.alerts.entities.AlertType alertType = request.getAlertType() != null 
                ? request.getAlertType() 
                : io.storeyes.storeyes_coffee.alerts.entities.AlertType.NOT_TAPPED;
        
        // Check if an alert with the same alertDate, alertType, and storeId already exists
        if (alertRepository.findByExactAlertDateAndAlertTypeAndStoreId(request.getAlertDate(), alertType, store.getId()).isEmpty()) {
            // No existing alert found, create a new one
            Alert alert = Alert.builder()
                    .alertDate(request.getAlertDate())
                    .store(store)
                    .mainVideoUrl(request.getMainVideoUrl())
                    .productName(request.getProductName())
                    .imageUrl(request.getImageUrl())
                    .alertType(alertType)
                    .isProcessed(false)
                    .build();
            
            alertRepository.save(alert);
        }
    }
    
    /**
     * Get alerts by date and processed status (supports both exact date and date range).
     * Store is resolved from CurrentStoreContext (set by StoreContextInterceptor).
     * By default returns processed alerts, unless unprocessed=true.
     * If date is not provided, defaults to today's date.
     * If returnType=true, returns only alerts with alertType=RETURN.
     * If alertType is provided (NOT_TAPPED or RETURN), returns only alerts of that type (takes precedence over returnType).
     *
     * <p><b>Demo-store date substitution:</b> when the demo store mapping carries a non-null
     * {@code alertDate}, the DB query targets that fixed date instead of the caller-supplied
     * {@code date}. After fetching, each returned alert's {@code alertDate} is rewritten so
     * that its <em>date</em> portion matches the original caller-supplied {@code ?date=} value
     * while preserving the original time-of-day component.</p>
     */
    public List<Alert> getAlertsByDate(LocalDateTime date, LocalDateTime endDate, Boolean unprocessed, Boolean returnType, io.storeyes.storeyes_coffee.alerts.entities.AlertType alertType) {
        Long storeId = CurrentStoreContext.getCurrentStoreId();
        if (storeId == null) {
            throw new RuntimeException("Store context not found for current user");
        }

        // Per-store alert-type visibility. Read from the selected store (not the demo
        // data store): the demo mapping only redirects where alert data comes from,
        // while the visibility configuration belongs to the real store.
        boolean notTappedEnabled = true;
        boolean returnEnabled = true;
        var currentStore = storeRepository.findById(storeId).orElse(null);
        if (currentStore != null) {
            notTappedEnabled = currentStore.isNotTappedAlertsEnabled();
            returnEnabled = currentStore.isReturnAlertsEnabled();
            // Alerts locked until the activation date (default creation + 3 weeks);
            // null activation date (legacy stores) counts as active.
            if (!isAlertsActive(currentStore)) {
                return List.of();
            }
        }

        // Resolve demo-store context (data store + optional fixed alert date).
        DemoStoreDataSourceResolver.AlertsDataContext alertsCtx =
                demoStoreDataSourceResolver.resolveAlertsDataContext(storeId);
        Long dataStoreId = alertsCtx.dataStoreId();
        LocalDate demoAlertDate = alertsCtx.alertDate(); // null for non-demo stores

        boolean filterUnprocessed = Boolean.TRUE.equals(unprocessed);
        boolean filterReturnType = Boolean.TRUE.equals(returnType);
        // alertType param takes precedence; if not set, fall back to returnType for backward compat
        io.storeyes.storeyes_coffee.alerts.entities.AlertType filterAlertType = alertType != null
                ? alertType
                : (filterReturnType ? io.storeyes.storeyes_coffee.alerts.entities.AlertType.RETURN : null);

        // Default to today's date if not provided; keep a reference to rewrite results later.
        final LocalDateTime requestedDate = (date != null) ? date : LocalDate.now().atStartOfDay();

        // When the demo mapping carries a fixed alertDate, substitute the date portion of the
        // query parameters so we hit the actual rows in the source store.
        LocalDateTime queryDate;
        LocalDateTime queryEndDate;
        if (demoAlertDate != null) {
            queryDate    = demoAlertDate.atTime(requestedDate.toLocalTime());
            queryEndDate = (endDate != null) ? demoAlertDate.atTime(endDate.toLocalTime()) : null;
        } else {
            queryDate    = requestedDate;
            queryEndDate = endDate;
        }

        List<Alert> alerts;
        if (queryEndDate != null) {
            // Date range
            if (filterUnprocessed) {
                alerts = alertRepository.findUnprocessedByAlertDateBetweenAndStoreId(queryDate, queryEndDate, dataStoreId);
            } else {
                // Default list: processed alerts + unprocessed alerts judged TRUE_POSITIVE
                alerts = alertRepository.findDefaultListByAlertDateBetweenAndStoreId(queryDate, queryEndDate, dataStoreId);
            }
        } else {
            // Exact date (or defaulted to today)
            if (filterUnprocessed) {
                alerts = alertRepository.findUnprocessedByAlertDateAndStoreId(queryDate, dataStoreId);
            } else {
                // Default list: processed alerts + unprocessed alerts judged TRUE_POSITIVE
                alerts = alertRepository.findDefaultListByAlertDateAndStoreId(queryDate, dataStoreId);
            }
        }

        // When a fixed demo alertDate was used, rewrite each alert's date portion back to the
        // caller-supplied date so the response appears to belong to the requested date.
        if (demoAlertDate != null) {
            LocalDate targetDate = requestedDate.toLocalDate();
            List<Alert> rewritten = new ArrayList<>(alerts.size());
            for (Alert a : alerts) {
                if (a.getAlertDate() != null) {
                    a.setAlertDate(a.getAlertDate().toLocalTime().atDate(targetDate));
                }
                rewritten.add(a);
            }
            alerts = rewritten;
        }

        // Apply type / judgement filters
        final boolean allowNotTapped = notTappedEnabled;
        final boolean allowReturn = returnEnabled;
        return alerts.stream()
                .filter(a -> {
                    // Drop alert types disabled for this store (null type counts as NOT_TAPPED)
                    boolean isReturn = a.getAlertType() == io.storeyes.storeyes_coffee.alerts.entities.AlertType.RETURN;
                    if (isReturn ? !allowReturn : !allowNotTapped) return false;
                    // If alertType filter is set, only return matching alerts
                    if (filterAlertType != null) {
                        if (a.getAlertType() != filterAlertType) return false;
                    }
                    HumanJudgement h = a.getHumanJudgement();
                    // For unprocessed alerts, return only TRUE_POSITIVE alerts.
                    if (filterUnprocessed) {
                        return h == HumanJudgement.TRUE_POSITIVE;
                    }
                    // Otherwise, return alerts with humanJudgement NEW or TRUE_POSITIVE
                    return h == null || h == HumanJudgement.NEW || h == HumanJudgement.TRUE_POSITIVE;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Per-store alert-type visibility for the current user's selected store.
     * Reads the selected store directly (not the demo data store), since the
     * visibility configuration belongs to the real store.
     */
    public AlertSettingsDTO getAlertSettings() {
        long storeId = CurrentStoreContext.requireCurrentStoreId();
        var store = storeRepository.findById(storeId)
                .orElseThrow(() -> new RuntimeException("Store not found with id: " + storeId));
        return AlertSettingsDTO.builder()
                .notTappedEnabled(store.isNotTappedAlertsEnabled())
                .returnEnabled(store.isReturnAlertsEnabled())
                .alertsActive(isAlertsActive(store))
                .build();
    }

    /**
     * Alerts are locked until the store's activation date (default creation + 3 weeks);
     * a null activation date (legacy stores) counts as active.
     */
    private static boolean isAlertsActive(Store store) {
        LocalDateTime activation = store.getAlertsActivationDate();
        return activation == null || !LocalDateTime.now().isBefore(activation);
    }

    /**
     * Update human judgement directly via query
     */
    @Transactional
    public boolean updateHumanJudgement(Long alertId, HumanJudgement judgement) {
        LocalDateTime now = LocalDateTime.now();
        int updated = alertRepository.updateHumanJudgement(alertId, judgement, now);
        return updated > 0;
    }
    
    /**
     * Get alert by ID.
     * <p>If the current store is a demo store and {@code requestedDate} is provided (or defaults
     * to today), the alert's {@code alertDate} is rewritten so its <em>date</em> portion matches
     * {@code requestedDate} while preserving the original time-of-day component.</p>
     *
     * @param id            alert primary key
     * @param requestedDate caller-supplied {@code ?date=} value; may be {@code null} (today used)
     */
    public Alert getAlertById(Long id, LocalDate requestedDate) {
        Long storeId = CurrentStoreContext.getCurrentStoreId();
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Alert not found with id: " + id));

        if (storeId != null) {
            DemoStoreDataSourceResolver.AlertsDataContext ctx =
                    demoStoreDataSourceResolver.resolveAlertsDataContext(storeId);
            if (ctx.alertDate() != null && alert.getAlertDate() != null) {
                LocalDate targetDate = requestedDate != null ? requestedDate : LocalDate.now();
                alert.setAlertDate(alert.getAlertDate().toLocalTime().atDate(targetDate));
            }
        }
        return alert;
    }

    /**
     * Get alert by ID (no date rewriting — kept for internal callers that don't pass a date).
     */
    public Alert getAlertById(Long id) {
        return getAlertById(id, null);
    }
    
    /**
     * Get all alerts
     */
    public List<AlertDTO> getAllAlerts() {
        List<Alert> alerts = alertRepository.findAll();
        return alertMapper.toDTOList(alerts);
    }
    
    /**
     * Update secondary video URL, image URL and mark alert as processed
     */
    @Transactional
    public boolean updateSecondaryVideoAndMarkProcessed(Long alertId, String secondaryVideoUrl, String imageUrl) {
        LocalDateTime now = LocalDateTime.now();
        int updated = alertRepository.updateSecondaryVideoAndMarkProcessed(alertId, secondaryVideoUrl, imageUrl, now);
        return updated > 0;
    }
    
    /**
     * Get alert summaries (alertId and alertDate) for today by store_id
     * Returns alerts for today for the specified store
     */
    public List<AlertSummaryDTO> getTodayAlertsByStoreId(Long storeId) {
        // Get today's date
        LocalDateTime today = LocalDate.now().atStartOfDay();
        Long dataStoreId = demoStoreDataSourceResolver.resolveAlertsDataStoreId(storeId);

        // Find alerts for today and store
        List<Alert> alerts = alertRepository.findByTodayAndStoreId(today, dataStoreId);
        
        // Map to summary DTO
        return alerts.stream()
                .map(alert -> AlertSummaryDTO.builder()
                        .alertId(alert.getId())
                        .alertDate(alert.getAlertDate())
                        .build())
                .collect(Collectors.toList());
    }
    
    /**
     * Get alert summaries (alertId and alertDate) by date and store_id
     * Returns all alerts (regardless of processed status) for the specified date and store
     */
    public List<AlertSummaryDTO> getAlertSummariesByDateAndStoreId(LocalDate date, Long storeId) {
        Long dataStoreId = demoStoreDataSourceResolver.resolveAlertsDataStoreId(storeId);
        // Find alerts by date and store
        List<Alert> alerts = alertRepository.findByAlertDateAndStoreId(date, dataStoreId);
        
        // Map to summary DTO
        return alerts.stream()
                .map(alert -> AlertSummaryDTO.builder()
                        .alertId(alert.getId())
                        .alertDate(alert.getAlertDate())
                        .build())
                .collect(Collectors.toList());
    }
    
    /**
     * Get alert details with sales by alert ID.
     * <p>If the current store is a demo store and {@code requestedDate} is provided (or defaults
     * to today), the returned DTO's {@code alertDate} is rewritten so its <em>date</em> portion
     * matches {@code requestedDate} while preserving the original time-of-day component.</p>
     *
     * @param id            alert primary key
     * @param requestedDate caller-supplied {@code ?date=} value; may be {@code null} (today used)
     * @return AlertDetailsDTO with sales
     */
    public AlertDetailsDTO getAlertDetailsWithSales(Long id, LocalDate requestedDate) {
        Alert alert = alertRepository.findByIdWithSales(id)
                .orElseThrow(() -> new RuntimeException("Alert not found with id: " + id));

        // Use mapper to convert Alert to AlertDetailsDTO
        return alertMapper.toDetailsDTO(alert);
    }

    /**
     * Get alert details with sales by alert ID (no date rewriting — kept for internal callers).
     */
    public AlertDetailsDTO getAlertDetailsWithSales(Long id) {
        return getAlertDetailsWithSales(id, null);
    }
}

