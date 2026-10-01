package com.excelai.excel;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.*;

@Service
/**
 * 中文：封装 EasyExcel 的多工作表读写，并提供兼容旧工具的首表行映射读取。
 * English: Wraps EasyExcel multi-sheet reading/writing and keeps a first-sheet row-map reader for existing tools.
 */
public class ExcelService {
    public ExcelWorkbook readWorkbook(Path path) {
        Map<Integer, SheetBuilder> sheets = new LinkedHashMap<>();
        EasyExcel.read(path.toFile(), new AnalysisEventListener<Map<Integer, String>>() {
            @Override
            public void invokeHeadMap(Map<Integer, String> headerMap, AnalysisContext context) {
                int sheetNo = context.readSheetHolder().getSheetNo();
                int maxColumn = headerMap.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
                List<String> headers = new ArrayList<>();
                for (int column = 0; column <= maxColumn; column++) {
                    headers.add(Objects.toString(headerMap.get(column), ""));
                }
                sheets.put(sheetNo, new SheetBuilder(context.readSheetHolder().getSheetName(), headers));
            }

            @Override
            public void invoke(Map<Integer, String> rowData, AnalysisContext context) {
                SheetBuilder sheet = sheets.get(context.readSheetHolder().getSheetNo());
                if (sheet == null) return;
                List<String> row = new ArrayList<>();
                for (int column = 0; column < sheet.headers.size(); column++) {
                    row.add(Objects.toString(rowData.get(column), ""));
                }
                sheet.rows.add(row);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
            }
        }).doReadAll();

        List<ExcelWorkbook.Sheet> result = sheets.values().stream()
                .map(sheet -> new ExcelWorkbook.Sheet(sheet.name, sheet.headers, sheet.rows))
                .toList();
        return new ExcelWorkbook(result);
    }

    public List<ExcelWorkbook.SheetSchema> inspectWorkbook(Path path) {
        Map<Integer, ExcelWorkbook.SheetSchema> schemas = new LinkedHashMap<>();
        EasyExcel.read(path.toFile(), new AnalysisEventListener<Map<Integer, String>>() {
            @Override
            public void invokeHeadMap(Map<Integer, String> headerMap, AnalysisContext context) {
                int maxColumn = headerMap.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
                List<String> headers = new ArrayList<>();
                for (int column = 0; column <= maxColumn; column++) {
                    headers.add(Objects.toString(headerMap.get(column), ""));
                }
                int sheetNo = context.readSheetHolder().getSheetNo();
                schemas.put(sheetNo, new ExcelWorkbook.SheetSchema(context.readSheetHolder().getSheetName(), headers));
            }

            @Override
            public void invoke(Map<Integer, String> rowData, AnalysisContext context) {
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
            }
        }).doReadAll();
        return new ArrayList<>(schemas.values());
    }
    public List<Map<String, Object>> read(Path path) {
        ExcelWorkbook workbook = readWorkbook(path);
        if (workbook.sheets().isEmpty()) return new ArrayList<>();
        ExcelWorkbook.Sheet sheet = workbook.sheets().get(0);
        List<String> keys = uniqueHeaders(sheet.headers());
        List<Map<String, Object>> result = new ArrayList<>();
        for (List<String> values : sheet.rows()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int column = 0; column < keys.size(); column++) {
                row.put(keys.get(column), values.get(column));
            }
            result.add(row);
        }
        return result;
    }

    public void write(Path path, List<Map<String, Object>> rows) {
        // 中文：结果列顺序沿用第一行 Map 的插入顺序，确保表头和每行数据对齐。
        // English: Export columns follow the first row's insertion order so headers and every row stay aligned.
        if (rows.isEmpty()) {
            EasyExcel.write(path.toFile()).head(List.of(List.of("result"))).sheet().doWrite(List.of());
            return;
        }
        List<String> headers = new ArrayList<>(rows.get(0).keySet());
        List<List<String>> head = headers.stream().map(List::of).toList();
        List<List<String>> data = rows.stream()
                .map(row -> headers.stream().map(header -> Objects.toString(row.get(header), "")).toList())
                .toList();
        EasyExcel.write(path.toFile()).head(head).sheet("result").doWrite(data);
    }

    private List<String> uniqueHeaders(List<String> headers) {
        Set<String> used = new HashSet<>();
        List<String> keys = new ArrayList<>();
        for (int column = 0; column < headers.size(); column++) {
            String base = headers.get(column) == null || headers.get(column).isBlank()
                    ? "COL_" + column : headers.get(column);
            String key = base;
            int suffix = 2;
            while (!used.add(key)) key = base + "__" + suffix++;
            keys.add(key);
        }
        return keys;
    }

    private static final class SheetBuilder {
        private final String name;
        private final List<String> headers;
        private final List<List<String>> rows = new ArrayList<>();

        private SheetBuilder(String name, List<String> headers) {
            this.name = name;
            this.headers = headers;
        }
    }
}