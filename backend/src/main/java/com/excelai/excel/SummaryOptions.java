package com.excelai.excel;

import java.util.List;

/** 中文：定义汇总指标、维度、聚合方式和可选日期周期。English: Defines a summary measure, dimensions, aggregation, and optional date period. */
public record SummaryOptions(String measureColumn, List<String> dimensionColumns, String aggregation,
                             String dateColumn, String datePeriod, String dateFormat) {
    public SummaryOptions(String measureColumn, List<String> dimensionColumns, String aggregation, String dateColumn, String datePeriod) {
        this(measureColumn, dimensionColumns, aggregation, dateColumn, datePeriod, "yyyy-MM-dd");
    }

    public SummaryOptions {
        dimensionColumns = dimensionColumns == null ? List.of() : List.copyOf(dimensionColumns);
        aggregation = aggregation == null ? "SUM" : aggregation.trim().toUpperCase(java.util.Locale.ROOT);
        datePeriod = datePeriod == null || datePeriod.isBlank() ? null : datePeriod.trim().toUpperCase(java.util.Locale.ROOT);
        dateFormat = dateFormat == null || dateFormat.isBlank() ? "yyyy-MM-dd" : dateFormat;
        if (!List.of("SUM", "COUNT").contains(aggregation)) throw new IllegalArgumentException("Aggregation must be SUM or COUNT");
        if (datePeriod != null && !List.of("DAY", "MONTH", "QUARTER", "YEAR").contains(datePeriod)) throw new IllegalArgumentException("Unsupported date period");
        if ((dateColumn == null) != (datePeriod == null)) throw new IllegalArgumentException("Date column and period must be supplied together");
    }
}
