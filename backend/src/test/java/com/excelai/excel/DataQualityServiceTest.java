package com.excelai.excel;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class DataQualityServiceTest {
    private final DataQualityService quality = new DataQualityService();

    @Test
    void reportsDuplicateHeadersBlankCellsAndDuplicateRowsWithoutCellValues() {
        ExcelWorkbook workbook = new ExcelWorkbook(List.of(new ExcelWorkbook.Sheet("orders",
                List.of("订单号", "金额", "金额"),
                List.of(List.of("A-1", "10", "10"), List.of("A-1", "10", "10"), List.of("", "", "")))));

        DataQualityService.QualityReport report = quality.analyze(workbook);
        Map<String, DataQualityService.Finding> findings = report.sheets().get(0).findings().stream()
                .collect(Collectors.toMap(DataQualityService.Finding::code, Function.identity()));

        assertEquals(1, findings.get("DUPLICATE_HEADERS").count());
        assertEquals(List.of(2, 3), findings.get("DUPLICATE_HEADERS").columns());
        assertEquals(3, findings.get("EMPTY_CELLS").count());
        assertEquals(List.of(4), findings.get("EMPTY_CELLS").sampleRows());
        assertEquals(1, findings.get("DUPLICATE_ROWS").count());
        assertEquals(List.of(3), findings.get("DUPLICATE_ROWS").sampleRows());
        assertFalse(report.toString().contains("A-1"));
        assertFalse(report.toString().contains("10"));
    }

    @Test
    void reportsMixedDateAndAmountFormatsWithSpreadsheetRowNumbers() {
        ExcelWorkbook workbook = new ExcelWorkbook(List.of(new ExcelWorkbook.Sheet("sales",
                List.of("订单日期", "销售额"),
                List.of(List.of("2026-01-01", "100"), List.of("待确认", "未知"), List.of("2026/01/03", "￥200")))));

        DataQualityService.SheetReport report = quality.analyze(workbook).sheets().get(0);

        assertEquals(1, report.findings().stream().filter(item -> item.code().equals("INCONSISTENT_DATE_VALUES"))
                .findFirst().orElseThrow().count());
        assertEquals(List.of(3), report.findings().stream().filter(item -> item.code().equals("INCONSISTENT_DATE_VALUES"))
                .findFirst().orElseThrow().sampleRows());
        assertEquals(1, report.findings().stream().filter(item -> item.code().equals("INCONSISTENT_AMOUNT_VALUES"))
                .findFirst().orElseThrow().count());
    }

    @Test
    void returnsNoFindingsForConsistentRows() {
        ExcelWorkbook workbook = new ExcelWorkbook(List.of(new ExcelWorkbook.Sheet("sales",
                List.of("订单日期", "销售额"),
                List.of(List.of("2026-01-01", "100"), List.of("2026-01-02", "200")))));

        assertTrue(quality.analyze(workbook).sheets().get(0).findings().isEmpty());
    }
}
