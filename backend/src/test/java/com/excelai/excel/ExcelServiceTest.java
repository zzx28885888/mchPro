package com.excelai.excel;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExcelServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void readWorkbookReturnsEverySheetInOrder() throws Exception {
        Path path = createWorkbook(List.of(List.of("Date"), List.of("Region")), List.of(List.of("2026-01-01", "East")), List.of(List.of("2026-02-01", "West")));
        ExcelWorkbook workbook = new ExcelService().readWorkbook(path);
        assertEquals(List.of("January", "February"), workbook.sheets().stream().map(ExcelWorkbook.Sheet::name).toList());
        assertEquals(List.of("2026-01-01", "East"), workbook.sheets().get(0).rows().get(0));
        assertEquals(List.of("2026-02-01", "West"), workbook.sheets().get(1).rows().get(0));
    }

    @Test
    void readsAllSheetsAndPreservesHeaderConflicts() throws Exception {
        Path path = createWorkbook(List.of(List.of("SKU"), List.of(""), List.of("SKU")), List.of(List.of("A-1", "", "A-2")), null);
        ExcelWorkbook.Sheet sheet = new ExcelService().readWorkbook(path).sheets().get(0);
        assertEquals(List.of("SKU", "", "SKU"), sheet.headers());
        assertEquals(List.of("A-1", "", "A-2"), sheet.rows().get(0));
    }

    @Test
    void writeWorkbookCreatesNamedSheetsAndChartsWhenSupported() throws Exception {
        Path path = tempDir.resolve("outputs.xlsx");
        new ExcelService().writeWorkbook(path, List.of(
                new OutputSheet("summary", List.of("Region", "Total"), List.of(List.of("East", "10"))),
                new OutputSheet("details", List.of("ID"), List.of(List.of("1")))));
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(path.toFile())) {
            assertEquals(List.of("summary", "details"), java.util.stream.IntStream.range(0, workbook.getNumberOfSheets()).mapToObj(workbook::getSheetName).toList());
            assertEquals(10.0, workbook.getSheet("summary").getRow(1).getCell(1).getNumericCellValue());
            assertEquals(1, workbook.getSheet("summary").getDrawingPatriarch().getCharts().size());
        }
    }

    private Path createWorkbook(List<List<String>> headers, List<List<String>> firstRows, List<List<String>> secondRows) throws Exception {
        Path path = tempDir.resolve("input.xlsx");
        ExcelWriter writer = EasyExcel.write(path.toFile()).build();
        writer.write(firstRows, EasyExcel.writerSheet(0, "January").head(headers).build());
        if (secondRows != null) writer.write(secondRows, EasyExcel.writerSheet(1, "February").head(headers).build());
        writer.finish();
        return path;
    }
}
