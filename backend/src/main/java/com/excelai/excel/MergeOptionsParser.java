package com.excelai.excel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MergeOptionsParser {
    private MergeOptionsParser() { }

    public static MergeOptions parse(Map<String, Object> options) {
        Map<String, List<String>> aliases = stringListMap(options == null ? null : options.get("headerAliases"));
        List<String> duplicateKeys = strings(options == null ? null : options.get("duplicateKeyColumns"));
        Map<String, String> dateFormats = stringMap(options == null ? null : options.get("dateFormats"));
        Set<String> amounts = Set.copyOf(strings(options == null ? null : options.get("amountColumns")));
        boolean skipBlankRows = options == null || !Boolean.FALSE.equals(options.get("skipBlankRows"));
        return new MergeOptions(aliases, duplicateKeys, skipBlankRows, dateFormats, amounts);
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(String::valueOf).toList();
    }

    private static Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), String.valueOf(item)));
        return result;
    }

    private static Map<String, List<String>> stringListMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, List<String>> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), strings(item)));
        return result;
    }
}
