package com.excelai.task;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskWorkerTest {
    @Test
    void removesHistoryMetadataFromOptionsSentToWorkflow() {
        Map<String, Object> stored = Map.of(
                "measureColumn", "amount",
                "workflowTemplateId", "monthly_sales_summary",
                "schemaSnapshot", Map.of("version", 1));

        Map<String, Object> execution = TaskWorker.executionOptions(stored);

        assertEquals(Map.of("measureColumn", "amount"), execution);
        assertTrue(stored.containsKey("schemaSnapshot"));
        assertEquals("monthly_sales_summary", stored.get("workflowTemplateId"));
    }
}
