package com.excelai.task;

import com.excelai.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskControllerTest {
    @Test
    void legacySingleFileRequestRoutesToCompatibilityMethod() {
        TaskService service = mock(TaskService.class);
        when(service.create(17L, 3L, "sort by date")).thenReturn(81L);
        TaskController controller = new TaskController(service, new ObjectMapper());

        Map<String, Object> response = controller.create(
                new TaskController.Req(null, 3L, null, null, "sort by date", null), authentication(17L));

        assertEquals(Map.of("taskId", 81L, "status", "QUEUED"), response);
        verify(service).create(17L, 3L, "sort by date");
    }

    @Test
    void typedRequestResolvesOrderedFileIds() {
        TaskController.Req request = new TaskController.Req(List.of(9L, 4L), null,
                WorkflowType.RECONCILE, Map.of("keyColumns", List.of("order_id")), "compare", "fp");

        assertEquals(List.of(9L, 4L), request.resolvedFileIds());
        assertFalse(request.isLegacy());
    }

    @Test
    void requestRejectsBothLegacyAndMultiFileIds() {
        TaskController.Req request = new TaskController.Req(List.of(9L), 4L,
                WorkflowType.SUMMARY, Map.of(), "summary", "fp");

        assertThrows(ApiException.class, request::resolvedFileIds);
    }

    @Test
    void taskGetReturnsStructuredPersistedWorkflowSummary() {
        TaskService service = mock(TaskService.class);
        var summary = "{\"workflowType\":\"SUMMARY\",\"counts\":{\"groupCount\":2},\"exceptions\":[]}";
        when(service.get(81L, 17L)).thenReturn(new TaskRepository.Task(81L, 17L, 3L, "summary", "SUMMARY", "{}", "COMPLETED", 100, 90L, null, summary));
        TaskController controller = new TaskController(service, new ObjectMapper());
        Map<String, Object> response = controller.get(81L, authentication(17L));
        assertEquals("SUMMARY", response.get("workflowType"));
        assertEquals(2, ((Map<?, ?>) response.get("resultSummary")).get("counts") instanceof Map<?, ?> counts ? counts.get("groupCount") : null);
    }
    private Authentication authentication(Long userId) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userId);
        return authentication;
    }
}