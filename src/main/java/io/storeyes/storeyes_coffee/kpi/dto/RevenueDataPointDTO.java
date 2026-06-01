package io.storeyes.storeyes_coffee.kpi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueDataPointDTO {

    /**
     * Label for the data point on the chart.
     * <ul>
     *   <li>DAILY   — "yyyy-MM-dd" (e.g. "2026-04-01")</li>
     *   <li>WEEKLY  — "W{n}" counting from oldest window (e.g. "W1")</li>
     *   <li>MONTHLY — "MMM yyyy" in French locale (e.g. "avr. 2026")</li>
     * </ul>
     */
    private String label;

    private double revenue;
}
