package com.excelai.excel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 中文：合并和清洗的显式规则；映射键为输出列，值为可接受的来源表头。English: Explicit merge and cleanup rules; keys are output columns and values are accepted source headers. */
public record MergeOptions(Map<String, List<String>> headerAliases, List<String> duplicateKeyColumns,
                           boolean skipBlankRows, Map<String, String> dateFormats, Set<String> amountColumns) {
    public MergeOptions {
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        if (headerAliases != null) headerAliases.forEach((key, value) -> aliases.put(key, List.copyOf(value)));
        headerAliases = Collections.unmodifiableMap(aliases);
        duplicateKeyColumns = duplicateKeyColumns == null ? List.of() : List.copyOf(duplicateKeyColumns);
        dateFormats = dateFormats == null ? Map.of() : Map.copyOf(dateFormats);
        amountColumns = amountColumns == null ? Set.of() : Set.copyOf(amountColumns);
    }
}
