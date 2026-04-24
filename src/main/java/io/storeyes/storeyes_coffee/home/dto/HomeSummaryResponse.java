package io.storeyes.storeyes_coffee.home.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeSummaryResponse {

    /** Month profit in MAD; same definition as statistics month KPI profit. */
    private BigDecimal monthProfit;

    /** Processed alerts visible on the default list for {@link #getDisplayDate()}. */
    private long alertsCount;

    /**
     * Total TTC for {@link #getDisplayDate()} when that date exists in the KPI date dimension;
     * {@code null} if there is no date dimension row (same as an empty daily-report response for unknown dates).
     */
    private Double dailyRevenueTtc;

    /** Calendar day used for alerts count and daily TTC ({@code yyyy-MM-dd}). */
    private String displayDate;

    /** Calendar month of {@link #getDisplayDate()} for {@link #getMonthProfit()} ({@code yyyy-MM}). */
    private String monthKey;
}
