package com.excelai.excel;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 中文：生成不回显单元格内容的表格质量提示；所有规则只报告问题，不修改数据。
 * English: Builds spreadsheet quality warnings without exposing cell values; checks report issues without changing data.
 */
@Service
public class DataQualityService {
    private static final int MAX_SAMPLE_ROWS = 5;
    private static final Set<String> DATE_HEADER_HINTS = Set.of("date", "time", "日期", "时间");
    private static final Set<String> AMOUNT_HEADER_HINTS = Set.of(
            "amount", "price", "total", "revenue", "cost", "金额", "单价", "总额", "销售额", "收入", "成本", "价格");
    private static final List<String> DATE_PATTERNS = List.of(
            "uuuu-MM-dd", "uuuu/M/d", "uuuu/M/dd", "uuuu/MM/d", "uuuu/MM/dd", "uuuuMMdd", "M/d/uuuu", "MM/dd/uuuu");

    public QualityReport analyze(ExcelWorkbook workbook) {
        List<SheetReport> reports = workbook.sheets().stream().map(this::analyze).toList();
        return new QualityReport(reports);
    }

    private SheetReport analyze(ExcelWorkbook.Sheet sheet) {
        List<Finding> findings = new ArrayList<>();
        addDuplicateHeaders(sheet, findings);
        addEmptyCells(sheet, findings);
        addDuplicateRows(sheet, findings);
        addInconsistentValues(sheet, findings);
        return new SheetReport(sheet.name(), sheet.rows().size(), findings);
    }

    private void addDuplicateHeaders(ExcelWorkbook.Sheet sheet, List<Finding> findings) {
        Map<String, List<Integer>> columnsByHeader = new LinkedHashMap<>();
        for (int i = 0; i < sheet.headers().size(); i++) {
            String header = sheet.headers().get(i) == null ? "" : sheet.headers().get(i).trim().toLowerCase(Locale.ROOT);
            if (!header.isEmpty()) columnsByHeader.computeIfAbsent(header, ignored -> new ArrayList<>()).add(i + 1);
        }
        List<List<Integer>> duplicateGroups = columnsByHeader.values().stream().filter(columns -> columns.size() > 1).toList();
        List<Integer> duplicateColumns = duplicateGroups.stream()
                .flatMap(List::stream).toList();
        long duplicateCount = duplicateGroups.stream().mapToLong(columns -> columns.size() - 1L).sum();
        if (duplicateCount > 0) findings.add(new Finding("DUPLICATE_HEADERS", duplicateCount, duplicateColumns, List.of(1)));
    }

    private void addEmptyCells(ExcelWorkbook.Sheet sheet, List<Finding> findings) {
        long count = 0;
        Set<Integer> columns = new LinkedHashSet<>();
        Set<Integer> rows = new LinkedHashSet<>();
        for (int rowIndex = 0; rowIndex < sheet.rows().size(); rowIndex++) {
            List<String> row = sheet.rows().get(rowIndex);
            for (int column = 0; column < sheet.headers().size(); column++) {
                if (column >= row.size() || row.get(column) == null || row.get(column).isBlank()) {
                    count++;
                    columns.add(column + 1);
                    rows.add(rowIndex + 2);
                }
            }
        }
        if (count > 0) findings.add(new Finding("EMPTY_CELLS", count, List.copyOf(columns), sample(rows)));
    }

    private void addDuplicateRows(ExcelWorkbook.Sheet sheet, List<Finding> findings) {
        Map<List<String>, Integer> firstRows = new HashMap<>();
        long count = 0;
        List<Integer> samples = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < sheet.rows().size(); rowIndex++) {
            List<String> row = sheet.rows().get(rowIndex);
            if (row.stream().allMatch(value -> value == null || value.isBlank())) continue;
            List<String> key = row.stream().map(value -> value == null ? "" : value).toList();
            if (firstRows.putIfAbsent(key, rowIndex + 2) != null) {
                count++;
                addSample(samples, rowIndex + 2);
            }
        }
        if (count > 0) findings.add(new Finding("DUPLICATE_ROWS", count, List.of(), List.copyOf(samples)));
    }

    private void addInconsistentValues(ExcelWorkbook.Sheet sheet, List<Finding> findings) {
        for (int column = 0; column < sheet.headers().size(); column++) {
            String header = sheet.headers().get(column) == null ? "" : sheet.headers().get(column).toLowerCase(Locale.ROOT);
            if (containsHint(header, DATE_HEADER_HINTS)) {
                addInvalidValues(sheet, column, "INCONSISTENT_DATE_VALUES", this::isDate, findings);
            } else if (containsHint(header, AMOUNT_HEADER_HINTS)) {
                addInvalidValues(sheet, column, "INCONSISTENT_AMOUNT_VALUES", this::isAmount, findings);
            }
        }
    }

    private void addInvalidValues(ExcelWorkbook.Sheet sheet, int column, String code,
                                  java.util.function.Predicate<String> validValue, List<Finding> findings) {
        int valid = 0;
        long invalid = 0;
        List<Integer> samples = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < sheet.rows().size(); rowIndex++) {
            List<String> row = sheet.rows().get(rowIndex);
            String value = column < row.size() ? row.get(column) : "";
            if (value == null || value.isBlank()) continue;
            if (validValue.test(value.trim())) valid++;
            else {
                invalid++;
                addSample(samples, rowIndex + 2);
            }
        }
        if (valid > 0 && invalid > 0) findings.add(new Finding(code, invalid, List.of(column + 1), List.copyOf(samples)));
    }

    private boolean isDate(String value) {
        for (String pattern : DATE_PATTERNS) {
            try {
                LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern, Locale.ROOT).withResolverStyle(ResolverStyle.STRICT));
                return true;
            } catch (RuntimeException ignored) {
            }
        }
        return false;
    }

    private boolean isAmount(String value) {
        String normalized = value.replaceAll("[\\p{Sc}\\s]", "");
        if (normalized.contains(",") && normalized.contains(".")) normalized = normalized.replace(",", "");
        else if (normalized.contains(",")) normalized = normalized.replace(',', '.');
        if (!Pattern.matches("[+-]?\\d+(\\.\\d+)?", normalized)) return false;
        try {
            new BigDecimal(normalized);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean containsHint(String header, Set<String> hints) {
        return hints.stream().anyMatch(header::contains);
    }

    private List<Integer> sample(Set<Integer> rows) {
        return rows.stream().limit(MAX_SAMPLE_ROWS).toList();
    }

    private void addSample(List<Integer> samples, int row) {
        if (samples.size() < MAX_SAMPLE_ROWS) samples.add(row);
    }

    public record QualityReport(List<SheetReport> sheets) {
        public QualityReport { sheets = List.copyOf(sheets); }
    }

    public record SheetReport(String name, int dataRows, List<Finding> findings) {
        public SheetReport { findings = List.copyOf(findings); }
    }

    public record Finding(String code, long count, List<Integer> columns, List<Integer> sampleRows) {
        public Finding {
            columns = List.copyOf(columns);
            sampleRows = List.copyOf(sampleRows);
        }
    }
}
