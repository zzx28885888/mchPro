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
}
