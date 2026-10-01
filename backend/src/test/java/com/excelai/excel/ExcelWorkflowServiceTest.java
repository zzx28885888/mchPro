package com.excelai.excel;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ExcelWorkflowServiceTest {    private static Map<String, List<String>> orderedMap(Object... pairs) {
        var map = new java.util.LinkedHashMap<String, List<String>>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], (List<String>) pairs[i + 1]);
        return map;
    }
    @Test
    void mergeMapsExplicitHeaderAliases() {
        var inputs = List.of(
                new SheetInput(10L, "Jan", List.of("SKU", "Amount"), List.of(List.of("A", "1.20"))),
                new SheetInput(11L, "Feb", List.of("Product Code", "Revenue"), List.of(List.of("B", "2.30"))));
        var options = new MergeOptions(orderedMap("SKU", List.of("SKU", "Product Code"), "Amount", List.of("Amount", "Revenue")), List.of(), true, Map.of(), Set.of());
        WorkflowResult result = new ExcelWorkflowService().mergeAndClean(inputs, options);
        assertEquals(List.of(List.of("A", "1.20"), List.of("B", "2.30")), result.outputSheets().get(0).rows());
        assertTrue(result.exceptions().isEmpty());
    }

    @Test
    void mergeReportsConflictingAndUnmappedHeaders() {
        var inputs = List.of(
                new SheetInput(10L, "Jan", List.of("SKU", "Name"), List.of(List.of("A", "one"))),
                new SheetInput(11L, "Feb", List.of("Item", "Name", "Note"), List.of(List.of("B", "two", "x"))));
        var options = new MergeOptions(orderedMap("SKU", List.of("SKU", "Item"), "Name", List.of("Name", "Item")), List.of(), true, Map.of(), Set.of());
        WorkflowResult result = new ExcelWorkflowService().mergeAndClean(inputs, options);
        assertTrue(result.exceptions().stream().anyMatch(e -> e.code().equals("UNMAPPED_HEADER")));
        assertTrue(result.exceptions().stream().anyMatch(e -> e.code().equals("CONFLICTING_HEADER")));
    }

    @Test
    void cleanupRemovesConfiguredDuplicatesAndBlankRowsWithoutMutatingSources() {
        var rows = List.of(List.of("A", "2026-01-02", "1.2"), List.of("A", "2026-01-02", "1.2"), List.of("", "", ""));
        var source = new SheetInput(10L, "Data", List.of("SKU", "Date", "Amount"), rows);
        var before = source.rows();
        var options = new MergeOptions(orderedMap("SKU", List.of("SKU"), "Date", List.of("Date"), "Amount", List.of("Amount")), List.of("SKU"), true, Map.of("Date", "yyyy-MM-dd"), Set.of("Amount"));
        WorkflowResult result = new ExcelWorkflowService().mergeAndClean(List.of(source), options);
        assertEquals(1, result.outputSheets().get(0).rows().size());
        assertEquals(before, source.rows());
        assertEquals(1, result.counts().get("duplicatesRemoved"));
        assertEquals(1, result.counts().get("blankRowsSkipped"));
    }

    @Test
    void cleanupReportsInvalidDateOrAmount() {
        var input = new SheetInput(10L, "Data", List.of("SKU", "Date", "Amount"), List.of(List.of("A", "not-a-date", "$oops")));
        var options = new MergeOptions(orderedMap("SKU", List.of("SKU"), "Date", List.of("Date"), "Amount", List.of("Amount")), List.of(), true, Map.of("Date", "yyyy-MM-dd"), Set.of("Amount"));
        WorkflowResult result = new ExcelWorkflowService().mergeAndClean(List.of(input), options);
        assertTrue(result.exceptions().stream().anyMatch(e -> e.code().equals("INVALID_DATE")));
        assertTrue(result.exceptions().stream().anyMatch(e -> e.code().equals("INVALID_AMOUNT")));
    }
    @Test
    void reconciliationReportsBlankAndDuplicateKeys() {
        var left = new SheetInput(20L, "L", List.of("ID", "Amount"), List.of(List.of("A", "10"), List.of("A", "11"), List.of("", "12")));
        var right = new SheetInput(21L, "R", List.of("Ref", "Value"), List.of(List.of("A", "10")));
        var options = reconcileOptions("ID", "Ref", "Amount", "Value", "0");
        WorkflowResult result = new ExcelWorkflowService().reconcile(left, right, options);
        assertEquals(4, result.outputSheets().stream().filter(s -> s.name().equals("AMBIGUOUS_KEY")).findFirst().orElseThrow().rows().size());
        assertEquals(0, result.outputSheets().stream().filter(s -> s.name().equals("MATCHED")).findFirst().orElseThrow().rows().size());
    }

    @Test
    void reconciliationAppliesInclusiveToleranceAndReportsInvalidNumbers() {
        var left = new SheetInput(20L, "L", List.of("ID", "Amount"), List.of(List.of("A", "10.00"), List.of("B", "oops")));
        var right = new SheetInput(21L, "R", List.of("Ref", "Value"), List.of(List.of("A", "10.05"), List.of("B", "8.00")));
        var options = reconcileOptions("ID", "Ref", "Amount", "Value", "0.05");
        WorkflowResult result = new ExcelWorkflowService().reconcile(left, right, options);
        assertEquals(1, result.outputSheets().stream().filter(s -> s.name().equals("MATCHED")).findFirst().orElseThrow().rows().size());
        assertEquals(1, result.exceptions().stream().filter(e -> e.code().equals("INVALID_NUMBER")).count());
    }

    @Test
    void reconciliationPreservesUnmatchedRows() {
        var left = new SheetInput(20L, "L", List.of("ID", "Amount"), List.of(List.of("A", "10"), List.of("B", "20")));
        var right = new SheetInput(21L, "R", List.of("Ref", "Value"), List.of(List.of("C", "30")));
        WorkflowResult result = new ExcelWorkflowService().reconcile(left, right, reconcileOptions("ID", "Ref", "Amount", "Value", "0"));
        assertEquals(2, result.outputSheets().stream().filter(s -> s.name().equals("LEFT_ONLY")).findFirst().orElseThrow().rows().size());
        assertEquals(1, result.outputSheets().stream().filter(s -> s.name().equals("RIGHT_ONLY")).findFirst().orElseThrow().rows().size());
    }

    private static ReconcileOptions reconcileOptions(String leftKey, String rightKey, String leftValue, String rightValue, String tolerance) {
        return new ReconcileOptions(Map.of("id", leftKey), Map.of("id", rightKey), Map.of("amount", leftValue), Map.of("amount", rightValue), new java.math.BigDecimal(tolerance));
    }    @Test
    void summaryGroupsAndRanksByConfiguredDimensions() {
        var input = new SheetInput(30L, "Sales", List.of("Region", "Amount"), List.of(List.of("East", "10"), List.of("West", "25"), List.of("East", "5")));
        var result = new ExcelWorkflowService().summarize(input, new SummaryOptions("Amount", List.of("Region"), "SUM", null, null));
        assertEquals(List.of(List.of("West", "25"), List.of("East", "15")), result.outputSheets().get(0).rows());
    }

    @Test
    void summaryReportsInvalidDatesAndPeriodDefinition() {
        var input = new SheetInput(30L, "Sales", List.of("Date", "Region", "Amount"), List.of(List.of("2026-01-02", "East", "10"), List.of("bad-date", "West", "20")));
        var result = new ExcelWorkflowService().summarize(input, new SummaryOptions("Amount", List.of("Region"), "SUM", "Date", "MONTH", "yyyy-MM-dd"));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("MONTH")));
        assertEquals(1, result.exceptions().stream().filter(e -> e.code().equals("INVALID_DATE")).count());
    }

    @Test
    void summaryRejectsUnknownMeasureOrDimension() {
        var input = new SheetInput(30L, "Sales", List.of("Region", "Amount"), List.of(List.of("East", "10")));
        assertThrows(IllegalArgumentException.class, () -> new ExcelWorkflowService().summarize(input, new SummaryOptions("Missing", List.of("Region"), "SUM", null, null)));
        assertThrows(IllegalArgumentException.class, () -> new ExcelWorkflowService().summarize(input, new SummaryOptions("Amount", List.of("Missing"), "SUM", null, null)));
    }}
