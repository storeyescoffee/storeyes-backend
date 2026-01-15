package io.storeyes.storeyes_coffee.kpi.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
    name = "date_dimensions",
    indexes = {
        @Index(name = "idx_date_dimensions_date", columnList = "date"),
        @Index(name = "idx_date_dimensions_year_month", columnList = "year,month")
    }
)
public class DateDimension {
    

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "date_dimension_id_seq")
    @SequenceGenerator(name = "date_dimension_id_seq", sequenceName = "date_dimension_id_seq", allocationSize = 1)
    @Column(name = "id", columnDefinition = "BIGINT DEFAULT nextval('date_dimension_id_seq')")
    private Long id;

    @Column(name = "date", nullable = false)
    @Temporal(TemporalType.DATE)
    private LocalDate date;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "month", nullable = false)
    private Integer month; // 1=January, 2=February, 3=March, 4=April, 5=May, 6=June, 7=July, 8=August, 9=September, 10=October, 11=November, 12=December

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek; // 0=Sunday, 1=Monday, 2=Tuesday, 3=Wednesday, 4=Thursday, 5=Friday, 6=Saturday
}
