package com.excelai.excel;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
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

    public void writeWorkbook(Path path, List<OutputSheet> outputSheets) {
        ExcelWriter writer = EasyExcel.write(path.toFile()).build();
        try {
            int index = 0;
            for (OutputSheet output : outputSheets) {
                List<List<String>> head = output.headers().stream().map(List::of).toList();
                List<List<String>> data = output.rows();
                String name = safeSheetName(output.name(), index++);
                writer.write(data, EasyExcel.writerSheet(name).head(head).build());
            }
        } finally {
            writer.finish();
        }
        addBasicChartsWhenSupported(path, outputSheets);
    }

    private String safeSheetName(String name, int index) {
        String safe = name == null ? "" : name.replaceAll("[\\\\/?*\\[\\]:]", "_").trim();
        if (safe.isEmpty()) safe = "Sheet" + (index + 1);
        return safe.length() > 31 ? safe.substring(0, 31) : safe;
    }

    private void addBasicChartsWhenSupported(Path path, List<OutputSheet> outputSheets) {
        try (var input = java.nio.file.Files.newInputStream(path);
             org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(input)) {
            boolean changed = false;
            for (OutputSheet output : outputSheets) {
                if (!"summary".equalsIgnoreCase(output.name()) || output.headers().size() != 2 || output.rows().isEmpty()) continue;
                String name = safeSheetName(output.name(), 0);
                org.apache.poi.xssf.usermodel.XSSFSheet sheet = workbook.getSheet(name);
                if (sheet == null) continue;
                List<Double> chartValues = new ArrayList<>();
                try {
                    for (List<String> row : output.rows()) chartValues.add(Double.parseDouble(row.get(1)));
                } catch (RuntimeException e) {
                    continue;
                }
                for (int row = 1; row <= chartValues.size(); row++) sheet.getRow(row).getCell(1).setCellValue(chartValues.get(row - 1));
                var drawing = sheet.createDrawingPatriarch();
                var anchor = drawing.createAnchor(0, 0, 0, 0, 3, 1, 12, 18);
                var chart = drawing.createChart(anchor);
                chart.setTitleText(output.name());
                var categories = chart.createCategoryAxis(org.apache.poi.xddf.usermodel.chart.AxisPosition.BOTTOM);
                var values = chart.createValueAxis(org.apache.poi.xddf.usermodel.chart.AxisPosition.LEFT);
                values.setCrosses(org.apache.poi.xddf.usermodel.chart.AxisCrosses.AUTO_ZERO);
                var categoryData = org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory.fromStringCellRange(sheet,
                        new org.apache.poi.ss.util.CellRangeAddress(1, output.rows().size(), 0, 0));
                var valueData = org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory.fromNumericCellRange(sheet,
                        new org.apache.poi.ss.util.CellRangeAddress(1, output.rows().size(), 1, 1));
                var data = chart.createData(org.apache.poi.xddf.usermodel.chart.ChartTypes.BAR, categories, values);
                data.addSeries(categoryData, valueData).setTitle(output.headers().get(1), null);
                chart.plot(data);
                changed = true;
            }
            if (changed) try (var output = java.nio.file.Files.newOutputStream(path)) { workbook.write(output); }
        } catch (Exception ignored) {
            // 中文：图表生成失败时保留已写出的数据工作簿。English: Keep the data workbook if optional chart generation fails.
        }
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