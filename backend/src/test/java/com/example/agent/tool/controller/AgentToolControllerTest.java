package com.example.agent.tool.controller;

import com.example.agent.audit.AuditService;
import com.example.agent.tool.annotation.AgentTool;
import com.example.agent.tool.model.ToolDefinition;
import com.example.agent.tool.model.ToolExecuteRequest;
import com.example.agent.tool.registry.RegisteredTool;
import com.example.agent.tool.registry.ToolRegistry;
import com.example.agent.tool.service.AgentToolContext;
import com.example.agent.tool.service.ToolExecutionService;
import com.excelai.plan.PlanRepository;
import com.excelai.task.TaskRepository;
import com.excelai.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentToolControllerTest {
    private ToolRegistry registry;
    private ToolExecutionService execution;
    private TaskRepository tasks;
    private UserRepository users;
    private PlanRepository plans;
    private AgentToolController controller;
    private TestTool bean;

    @BeforeEach
    void setUp() throws Exception {
        registry = mock(ToolRegistry.class);
        tasks = mock(TaskRepository.class);
        users = mock(UserRepository.class);
        plans = mock(PlanRepository.class);
        bean = new TestTool();
        var method = TestTool.class.getDeclaredMethod("merge");
        var definition = new ToolDefinition("merge_clean_workbooks", "merge", "excel:merge_clean_workbooks", 1000, Map.of());
        var registered = new RegisteredTool(definition, bean, method, Map.of());
        when(registry.get("merge_clean_workbooks")).thenReturn(registered);
        execution = new ToolExecutionService(registry, mock(AuditService.class));
        controller = new AgentToolController(registry, execution, tasks, users, plans);
        ReflectionTestUtils.setField(controller, "internalToken", "secret");
        when(tasks.owned(9L, 7L)).thenReturn(new TaskRepository.Task(9L, 7L, 11L, "merge", "MERGE_CLEAN",
                "{\"headerAliases\":{\"SKU\":[\"SKU\"]}}", "RUNNING", 10, null, null, null));
        when(tasks.inputFileIds(9L)).thenReturn(List.of(11L, 12L));
        when(users.findById(7L)).thenReturn(new UserRepository.User(7L, "a@example.com", "x", "BASIC"));
    }

    @Test
    void rejectsModelSuppliedFileIdsAsUnknownToolArguments() {
        when(plans.toolEnabled("BASIC", "merge_clean_workbooks")).thenReturn(true);
        var response = controller.execute("merge_clean_workbooks", "secret", request(
                Map.of("fileId", 99L), Map.of("inputFileIds", List.of(11L, 12L), "options", Map.of())));
        assertFalse(response.success());
        assertEquals("UNKNOWN_PARAMETER:fileId", response.error());
        assertEquals(0, bean.calls);
    }

    @Test
    void rejectsWorkflowToolWithoutPlanGrant() {
        when(plans.toolEnabled("BASIC", "merge_clean_workbooks")).thenReturn(false);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.execute("merge_clean_workbooks", "secret", request(Map.of(), Map.of("inputFileIds", List.of(11L, 12L)))));
        assertEquals(403, error.getStatusCode().value());
        assertEquals(0, bean.calls);
    }

    @Test
    void rejectsInputIdsThatDifferFromPersistedTaskInputs() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.execute("merge_clean_workbooks", "secret", request(Map.of(), Map.of("inputFileIds", List.of(11L, 99L)))));
        assertEquals(403, error.getStatusCode().value());
    }

    @Test
    void rebuildsWorkflowOptionsFromPersistedTaskContext() {
        when(plans.toolEnabled("BASIC", "merge_clean_workbooks")).thenReturn(true);
        var response = controller.execute("merge_clean_workbooks", "secret", request(Map.of(), Map.of("inputFileIds", List.of(11L, 12L), "options", Map.of("unsafe", true))));
        assertTrue(response.success());
        assertEquals(Map.of("headerAliases", Map.of("SKU", List.of("SKU"))), bean.options);
    }

    private ToolExecuteRequest request(Map<String, Object> arguments, Map<String, Object> extra) {
        var context = new java.util.LinkedHashMap<String, Object>(Map.of("userId", 7L, "taskId", 9L,
                "inputFileId", 11L, "workflowType", "MERGE_CLEAN"));
        context.putAll(extra);
        return new ToolExecuteRequest(arguments, "langgraph", context);
    }

    public static class TestTool {
        int calls;
        Map<?, ?> options;

        @AgentTool(name = "merge_clean_workbooks", description = "test", permission = "excel:merge_clean_workbooks", timeoutMs = 1000)
        public String merge() {
            calls++;
            options = (Map<?, ?>) AgentToolContext.get().get("options");
            return "ok";
        }
    }
}
