package com.excelai.excel;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 中文：定义左右工作簿的匹配键、比较字段和非负容差。English: Defines left/right matching keys, compared fields, and a non-negative tolerance. */
public record ReconcileOptions(Map<String, String> leftKeyMappings, Map<String, String> rightKeyMappings,
                               Map<String, String> leftValueMappings, Map<String, String> rightValueMappings,
                               BigDecimal tolerance) {
    public ReconcileOptions {
        leftKeyMappings = immutableOrdered(leftKeyMappings);
        rightKeyMappings = immutableOrdered(rightKeyMappings);
        leftValueMappings = immutableOrdered(leftValueMappings);
        rightValueMappings = immutableOrdered(rightValueMappings);
        tolerance = tolerance == null ? BigDecimal.ZERO : tolerance;
        if (tolerance.signum() < 0) throw new IllegalArgumentException("Tolerance must be non-negative");
        if (!new java.util.ArrayList<>(leftKeyMappings.keySet()).equals(new java.util.ArrayList<>(rightKeyMappings.keySet()))) throw new IllegalArgumentException("Left and right key names must match");
        if (!new java.util.ArrayList<>(leftValueMappings.keySet()).equals(new java.util.ArrayList<>(rightValueMappings.keySet()))) throw new IllegalArgumentException("Left and right value names must match");
    }

    private static Map<String, String> immutableOrdered(Map<String, String> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source == null ? Map.of() : source));
    }
}
