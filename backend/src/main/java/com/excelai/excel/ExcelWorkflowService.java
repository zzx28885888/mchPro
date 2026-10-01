package com.excelai.excel;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 中文：执行不依赖模型猜测的 Excel 业务转换。English: Runs deterministic Excel business transformations without model guesses. */
@Service
public class ExcelWorkflowService {
    public WorkflowResult mergeAndClean(List<SheetInput> inputs, MergeOptions options) {
        if (inputs == null || inputs.isEmpty()) throw new IllegalArgumentException("At least one source sheet is required");
        if (options == null || options.headerAliases().isEmpty()) throw new IllegalArgumentException("Explicit header mappings are required");
        List<String> outputHeaders = List.copyOf(options.headerAliases().keySet());
        List<List<String>> outputRows = new ArrayList<>();
        List<WorkflowResult.RowException> exceptions = new ArrayList<>();
        Set<List<String>> seenKeys = new HashSet<>();
        long sourceRows = 0, blankRows = 0, duplicates = 0;

        for (SheetInput input : inputs) {
            sourceRows += input.rows().size();
            Map<String, List<Integer>> columns = resolveColumns(input, options, exceptions);
            for (int rowIndex = 0; rowIndex < input.rows().size(); rowIndex++) {
                List<String> source = input.rows().get(rowIndex);
                int rowNumber = rowIndex + 2;
                if (source.stream().allMatch(value -> value == null || value.isBlank()) && options.skipBlankRows()) {
                    blankRows++;
                    exceptions.add(new WorkflowResult.RowException("BLANK_ROW_SKIPPED", input.fileId(), input.sheetName(), rowNumber, "", "Blank row skipped / 已跳过空行", ""));
                    continue;
                }
                List<String> output = new ArrayList<>();
                for (String target : outputHeaders) {
                    List<Integer> matches = columns.getOrDefault(target, List.of());
                    if (matches.size() != 1) {
                        output.add("");
                        if (matches.isEmpty()) exceptions.add(exception("UNMAPPED_HEADER", input, rowNumber, target, "Required header mapping is missing / 缺少必需的表头映射", ""));
                        else exceptions.add(exception("CONFLICTING_HEADER", input, rowNumber, target, "Multiple source columns map to this output / 多个来源列映射到同一输出列", ""));
                        continue;
                    }
                    String value = input.value(source, matches.get(0)).trim();
                    if (!value.isEmpty() && options.dateFormats().containsKey(target)) value = normalizeDate(value, options.dateFormats().get(target), input, rowNumber, target, exceptions);
                    if (!value.isEmpty() && options.amountColumns().contains(target)) value = normalizeAmount(value, input, rowNumber, target, exceptions);
                    output.add(value);
                }
                if (!options.duplicateKeyColumns().isEmpty()) {
                    List<Integer> keyIndexes = options.duplicateKeyColumns().stream().map(outputHeaders::indexOf).toList();
                    if (keyIndexes.stream().anyMatch(index -> index < 0)) throw new IllegalArgumentException("Duplicate keys must name mapped output columns");
                    List<String> key = keyIndexes.stream().map(output::get).toList();
                    if (key.stream().noneMatch(String::isBlank) && !seenKeys.add(key)) {
                        duplicates++;
                        exceptions.add(new WorkflowResult.RowException("DUPLICATE_ROW_SKIPPED", input.fileId(), input.sheetName(), rowNumber, String.join(",", options.duplicateKeyColumns()), "Duplicate row skipped / 已跳过重复行", String.join("|", key)));
                        continue;
                    }
                }
                outputRows.add(List.copyOf(output));
            }
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("sourceRows", sourceRows);
        counts.put("outputRows", (long) outputRows.size());
        counts.put("blankRowsSkipped", blankRows);
        counts.put("duplicatesRemoved", duplicates);
        counts.put("exceptionRows", (long) exceptions.size());
        return new WorkflowResult(List.of(new OutputSheet("merged_data", outputHeaders, outputRows)), counts, List.of(), exceptions);
    }

    private Map<String, List<Integer>> resolveColumns(SheetInput input, MergeOptions options, List<WorkflowResult.RowException> exceptions) {
        Map<String, List<Integer>> resolved = new LinkedHashMap<>();
        Set<String> mappedSourceHeaders = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> mapping : options.headerAliases().entrySet()) {
            List<Integer> indexes = new ArrayList<>();
            for (int i = 0; i < input.headers().size(); i++) {
                if (mapping.getValue().contains(input.headers().get(i))) indexes.add(i);
            }
            resolved.put(mapping.getKey(), indexes);
        }
        for (int i = 0; i < input.headers().size(); i++) {
            String header = input.headers().get(i);
            List<String> targets = options.headerAliases().entrySet().stream().filter(e -> e.getValue().contains(header)).map(Map.Entry::getKey).toList();
            if (targets.isEmpty()) {
                exceptions.add(new WorkflowResult.RowException("UNMAPPED_HEADER", input.fileId(), input.sheetName(), 1, header, "Source header is not mapped / 来源表头未映射", header));
            } else if (targets.size() > 1) {
                exceptions.add(new WorkflowResult.RowException("CONFLICTING_HEADER", input.fileId(), input.sheetName(), 1, header, "Source header maps to multiple outputs / 来源表头映射到多个输出", header));
                targets.forEach(target -> resolved.put(target, List.of()));
            }
            mappedSourceHeaders.add(header);
        }
        for (Map.Entry<String, List<Integer>> entry : resolved.entrySet()) {
            if (entry.getValue().size() > 1) exceptions.add(new WorkflowResult.RowException("CONFLICTING_HEADER", input.fileId(), input.sheetName(), 1, entry.getKey(), "Duplicate source headers match one output / 重复来源表头匹配到同一输出", entry.getKey()));
        }
        return resolved;
    }

    private String normalizeDate(String value, String pattern, SheetInput input, int row, String column, List<WorkflowResult.RowException> exceptions) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern.replace("yyyy", "uuuu"), Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
            return LocalDate.parse(value, formatter).toString();
        } catch (RuntimeException e) {
            exceptions.add(exception("INVALID_DATE", input, row, column, "Date could not be parsed with the configured format / 日期无法按配置格式解析", value));
            return value;
        }
    }

    private String normalizeAmount(String value, SheetInput input, int row, String column, List<WorkflowResult.RowException> exceptions) {
        try {
            String normalized = value.replaceAll("[\\p{Sc}\\s]", "");
            if (normalized.contains(",") && normalized.contains(".")) normalized = normalized.replace(",", "");
            else if (normalized.contains(",")) normalized = normalized.replace(',', '.');
            if (!Pattern.matches("[+-]?\\d+(\\.\\d+)?", normalized)) throw new NumberFormatException();
            return new BigDecimal(normalized).stripTrailingZeros().toPlainString();
        } catch (RuntimeException e) {
            exceptions.add(exception("INVALID_AMOUNT", input, row, column, "Amount could not be normalized / 金额无法标准化", value));
            return value;
        }
    }

    private WorkflowResult.RowException exception(String code, SheetInput input, int row, String column, String message, String value) {
        return new WorkflowResult.RowException(code, input.fileId(), input.sheetName(), row, column, message, value);
    }
    public WorkflowResult reconcile(SheetInput left, SheetInput right, ReconcileOptions options) {
        if (left == null || right == null || options == null) throw new IllegalArgumentException("Two sheets and reconciliation options are required");
        if (options.leftKeyMappings().isEmpty()) throw new IllegalArgumentException("At least one matching key is required");
        List<Integer> leftKeys = resolveRequiredColumns(left, options.leftKeyMappings());
        List<Integer> rightKeys = resolveRequiredColumns(right, options.rightKeyMappings());
        List<Integer> leftValues = resolveRequiredColumns(left, options.leftValueMappings());
        List<Integer> rightValues = resolveRequiredColumns(right, options.rightValueMappings());
        Map<String, List<Integer>> leftGroups = groupByKey(left, leftKeys);
        Map<String, List<Integer>> rightGroups = groupByKey(right, rightKeys);
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(leftGroups.keySet());
        allKeys.addAll(rightGroups.keySet());
        Map<String, List<List<String>>> categorized = new LinkedHashMap<>();
        for (String category : List.of("MATCHED", "LEFT_ONLY", "RIGHT_ONLY", "VALUE_DIFFERENCE", "AMBIGUOUS_KEY")) categorized.put(category, new ArrayList<>());
        List<WorkflowResult.RowException> exceptions = new ArrayList<>();

        for (String key : allKeys) {
            List<Integer> leftRows = leftGroups.getOrDefault(key, List.of());
            List<Integer> rightRows = rightGroups.getOrDefault(key, List.of());
            if (key.isEmpty() || leftRows.size() > 1 || rightRows.size() > 1) {
                for (int row : leftRows) {
                    categorized.get("AMBIGUOUS_KEY").add(reconcileRow(key, left, row, right, null, options, leftValues, rightValues, "AMBIGUOUS_KEY", ""));
                    exceptions.add(rowException("AMBIGUOUS_KEY", left, row, "Key is blank or duplicated; row was not paired / 匹配键为空或重复，未执行配对", key));
                }
                for (int row : rightRows) {
                    categorized.get("AMBIGUOUS_KEY").add(reconcileRow(key, left, null, right, row, options, leftValues, rightValues, "AMBIGUOUS_KEY", ""));
                    exceptions.add(rowException("AMBIGUOUS_KEY", right, row, "Key is blank or duplicated; row was not paired / 匹配键为空或重复，未执行配对", key));
                }
                continue;
            }
            if (leftRows.isEmpty()) {
                categorized.get("RIGHT_ONLY").add(reconcileRow(key, left, null, right, rightRows.get(0), options, leftValues, rightValues, "RIGHT_ONLY", ""));
                continue;
            }
            if (rightRows.isEmpty()) {
                categorized.get("LEFT_ONLY").add(reconcileRow(key, left, leftRows.get(0), right, null, options, leftValues, rightValues, "LEFT_ONLY", ""));
                continue;
            }
            int leftRow = leftRows.get(0), rightRow = rightRows.get(0);
            List<String> differences = new ArrayList<>();
            for (String field : options.leftValueMappings().keySet()) {
                String leftValue = left.value(left.rows().get(leftRow), leftValues.get(new ArrayList<>(options.leftValueMappings().keySet()).indexOf(field))).trim();
                String rightValue = right.value(right.rows().get(rightRow), rightValues.get(new ArrayList<>(options.rightValueMappings().keySet()).indexOf(field))).trim();
                BigDecimal l = null, r = null;
                try { l = parseNumber(leftValue); }
                catch (RuntimeException e) { exceptions.add(rowException("INVALID_NUMBER", left, leftRow, "Compared value is not numeric / 左侧比较值不是有效数字", leftValue)); }
                try { r = parseNumber(rightValue); }
                catch (RuntimeException e) { exceptions.add(rowException("INVALID_NUMBER", right, rightRow, "Compared value is not numeric / 右侧比较值不是有效数字", rightValue)); }
                if (l == null || r == null) differences.add(field + ":INVALID");
                else if (l.subtract(r).abs().compareTo(options.tolerance()) > 0) differences.add(field + ":" + l.subtract(r).toPlainString());
            }
            String category = differences.isEmpty() ? "MATCHED" : "VALUE_DIFFERENCE";
            categorized.get(category).add(reconcileRow(key, left, leftRow, right, rightRow, options, leftValues, rightValues, category, String.join(";", differences)));
        }
        List<String> headers = List.of("key", "leftFileId", "leftSheet", "leftRow", "rightFileId", "rightSheet", "rightRow", "status", "leftValues", "rightValues", "difference");
        List<OutputSheet> outputs = categorized.entrySet().stream().map(e -> new OutputSheet(e.getKey(), headers, e.getValue())).toList();
        Map<String, Long> counts = new LinkedHashMap<>();
        categorized.forEach((name, rows) -> counts.put(name, (long) rows.size()));
        counts.put("exceptionRows", (long) exceptions.size());
        return new WorkflowResult(outputs, counts, List.of(), exceptions);
    }

    private Map<String, List<Integer>> groupByKey(SheetInput input, List<Integer> keyColumns) {
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        for (int row = 0; row < input.rows().size(); row++) {
            List<String> parts = new ArrayList<>();
            for (int column : keyColumns) parts.add(input.value(input.rows().get(row), column).trim().toUpperCase(Locale.ROOT));
            String key = parts.stream().anyMatch(String::isBlank) ? "" : String.join("|", parts);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        return groups;
    }

    private List<Integer> resolveRequiredColumns(SheetInput input, Map<String, String> mappings) {
        List<Integer> indexes = new ArrayList<>();
        for (String header : mappings.values()) {
            List<Integer> matches = new ArrayList<>();
            for (int i = 0; i < input.headers().size(); i++) if (header.equals(input.headers().get(i))) matches.add(i);
            if (matches.size() != 1) throw new IllegalArgumentException("Required header is missing or ambiguous: " + header);
            indexes.add(matches.get(0));
        }
        return indexes;
    }

    private List<String> reconcileRow(String key, SheetInput left, Integer leftRow, SheetInput right, Integer rightRow,
                                      ReconcileOptions options, List<Integer> leftValues, List<Integer> rightValues,
                                      String status, String difference) {
        String lValues = leftRow == null ? "" : values(left, leftRow, options.leftValueMappings().keySet(), leftValues);
        String rValues = rightRow == null ? "" : values(right, rightRow, options.rightValueMappings().keySet(), rightValues);
        return List.of(key, leftRow == null ? "" : String.valueOf(left.fileId()), leftRow == null ? "" : left.sheetName(), leftRow == null ? "" : String.valueOf(leftRow + 2),
                rightRow == null ? "" : String.valueOf(right.fileId()), rightRow == null ? "" : right.sheetName(), rightRow == null ? "" : String.valueOf(rightRow + 2), status, lValues, rValues, difference);
    }

    private String values(SheetInput input, int row, Set<String> fields, List<Integer> columns) {
        List<String> names = new ArrayList<>(fields);
        List<String> values = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) values.add(names.get(i) + "=" + input.value(input.rows().get(row), columns.get(i)));
        return String.join(";", values);
    }

    private BigDecimal parseNumber(String value) {
        String normalized = value.replaceAll("[\\p{Sc}\\s]", "");
        if (normalized.contains(",") && normalized.contains(".")) normalized = normalized.replace(",", "");
        else if (normalized.contains(",")) normalized = normalized.replace(',', '.');
        if (!Pattern.matches("[+-]?\\d+(\\.\\d+)?", normalized)) throw new NumberFormatException("Invalid numeric value");
        return new BigDecimal(normalized);
    }

    private WorkflowResult.RowException rowException(String code, SheetInput input, int row, String message, String value) {
        return new WorkflowResult.RowException(code, input.fileId(), input.sheetName(), row + 2, "", message, value);
    }    public WorkflowResult summarize(SheetInput input, SummaryOptions options) {
        if (input == null || options == null) throw new IllegalArgumentException("A source sheet and summary options are required");
        List<Integer> dimensionIndexes = options.dimensionColumns().stream().map(name -> uniqueHeaderIndex(input, name)).toList();
        Integer measureIndex = options.measureColumn() == null || options.measureColumn().isBlank()
                ? null : uniqueHeaderIndex(input, options.measureColumn());
        if ("SUM".equals(options.aggregation()) && measureIndex == null) throw new IllegalArgumentException("SUM requires a measure column");
        Integer dateIndex = options.dateColumn() == null ? null : uniqueHeaderIndex(input, options.dateColumn());
        Map<List<String>, BigDecimal> totals = new LinkedHashMap<>();
        List<WorkflowResult.RowException> exceptions = new ArrayList<>();
        long includedRows = 0;
        for (int rowIndex = 0; rowIndex < input.rows().size(); rowIndex++) {
            List<String> row = input.rows().get(rowIndex);
            List<String> group = new ArrayList<>();
            for (int index : dimensionIndexes) group.add(input.value(row, index).trim());
            if (dateIndex != null) {
                String rawDate = input.value(row, dateIndex).trim();
                try {
                    LocalDate date = LocalDate.parse(rawDate, DateTimeFormatter.ofPattern(options.dateFormat().replace("yyyy", "uuuu"), Locale.ROOT).withResolverStyle(ResolverStyle.STRICT));
                    group.add(periodLabel(date, options.datePeriod()));
                } catch (RuntimeException e) {
                    exceptions.add(rowException("INVALID_DATE", input, rowIndex, "Date excluded from period grouping / 日期无效，已从周期分组排除", rawDate));
                    continue;
                }
            }
            BigDecimal value = BigDecimal.ONE;
            if ("SUM".equals(options.aggregation())) {
                try { value = parseNumber(input.value(row, measureIndex).trim()); }
                catch (RuntimeException e) {
                    exceptions.add(new WorkflowResult.RowException("INVALID_AMOUNT", input.fileId(), input.sheetName(), rowIndex + 2, options.measureColumn(), "Measure is not a valid number and was excluded / 指标不是有效数字，已排除", input.value(row, measureIndex)));
                    continue;
                }
            }
            totals.merge(List.copyOf(group), value, BigDecimal::add);
            includedRows++;
        }
        List<Map.Entry<List<String>, BigDecimal>> ranked = new ArrayList<>(totals.entrySet());
        ranked.sort(Map.Entry.<List<String>, BigDecimal>comparingByValue().reversed().thenComparing(entry -> String.join("|", entry.getKey()), String.CASE_INSENSITIVE_ORDER));
        List<String> headers = new ArrayList<>(options.dimensionColumns());
        if (dateIndex != null) headers.add(options.dateColumn() + "_" + options.datePeriod());
        headers.add(options.measureColumn() == null || options.measureColumn().isBlank() ? "count" : options.measureColumn() + "_" + options.aggregation().toLowerCase(Locale.ROOT));
        List<List<String>> outputRows = ranked.stream().map(entry -> {
            List<String> row = new ArrayList<>(entry.getKey());
            row.add(entry.getValue().stripTrailingZeros().toPlainString());
            return List.copyOf(row);
        }).toList();
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("sourceRows", (long) input.rows().size());
        counts.put("groupCount", (long) outputRows.size());
        counts.put("includedRows", includedRows);
        counts.put("exceptionRows", (long) exceptions.size());
        List<String> warnings = dateIndex == null ? List.of() : List.of("Date period: " + options.datePeriod() + " / 日期周期：" + options.datePeriod());
        return new WorkflowResult(List.of(new OutputSheet("summary", headers, outputRows)), counts, warnings, exceptions);
    }

    private int uniqueHeaderIndex(SheetInput input, String header) {
        List<Integer> matches = new ArrayList<>();
        for (int i = 0; i < input.headers().size(); i++) if (header.equals(input.headers().get(i))) matches.add(i);
        if (matches.size() != 1) throw new IllegalArgumentException("Required header is missing or ambiguous: " + header);
        return matches.get(0);
    }

    private String periodLabel(LocalDate date, String period) {
        return switch (period) {
            case "DAY" -> date.toString();
            case "MONTH" -> String.format(Locale.ROOT, "%04d-%02d", date.getYear(), date.getMonthValue());
            case "QUARTER" -> date.getYear() + "-Q" + ((date.getMonthValue() - 1) / 3 + 1);
            case "YEAR" -> String.valueOf(date.getYear());
            default -> throw new IllegalArgumentException("Unsupported date period: " + period);
        };
    }}
