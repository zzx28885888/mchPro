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
}
