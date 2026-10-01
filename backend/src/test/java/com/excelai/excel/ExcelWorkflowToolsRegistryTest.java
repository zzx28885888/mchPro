package com.excelai.excel;

import com.example.agent.tool.registry.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExcelWorkflowToolsRegistryTest {
    @Test
    void workflowToolsRegisterWithEmptyModelControlledArgumentSchemas() {
        ApplicationContext context = mock(ApplicationContext.class);
        ExcelWorkflowTools tools = new ExcelWorkflowTools(null, null, null, null, null, null, null);
        when(context.getBeansOfType(Object.class)).thenReturn(Map.of("excelWorkflowTools", tools));
        ToolRegistry registry = new ToolRegistry(context);
        Set<String> names = Set.of("inspect_workflow_inputs", "merge_clean_workbooks", "reconcile_workbooks", "summarize_workbook", "export_workbook_result");
        assertTrue(registry.all().stream().map(tool -> tool.definition().name()).collect(java.util.stream.Collectors.toSet()).containsAll(names));
        for (String name : names) {
            var registered = registry.get(name);
            assertEquals(Set.of(), registered.parameters().keySet());
            assertEquals(false, registered.definition().inputSchema().get("additionalProperties"));
        }
    }
}
