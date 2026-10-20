package com.excelai.task;

import com.excelai.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskControllerTest {
    @Test
    void legacySingleFileRequestRoutesToCompatibilityMethod() {
        TaskService service = mock(TaskService.class);
        when(service.create(17L, 3L, "sort by date")).thenReturn(81L);
        TaskController controller = new TaskController(service, mock(TaskReviewService.class), new ObjectMapper());

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
        TaskController controller = new TaskController(service, mock(TaskReviewService.class), new ObjectMapper());
        Map<String, Object> response = controller.get(81L, authentication(17L));
        assertEquals("SUMMARY", response.get("workflowType"));
        assertEquals(2, ((Map<?, ?>) response.get("resultSummary")).get("counts") instanceof Map<?, ?> counts ? counts.get("groupCount") : null);
    }

    @Test
    void taskHistoryReturnsRuleInputsAndResultMetadataForAuthenticatedUser() {
        var created = LocalDateTime.parse("2026-10-19T10:15:30");
        var historyTask = new TaskRepository.HistoryTask(81L, "COMPLETED", 100, "SUMMARY", "monthly report",
                "{\"measureColumn\":\"Amount\"}", created, created.plusMinutes(1), 90L, null,
                "{\"counts\":{\"groupCount\":2}}", List.of(new TaskRepository.HistoryInput(3L, "sales.xlsx")));
        TaskRepository.HistoryPage page = new TaskRepository.HistoryPage(List.of(historyTask), 0, 20, 1);
        TaskService service = mock(TaskService.class);
        when(service.history(17L, 0, 20, "COMPLETED", "SUMMARY", null, null)).thenReturn(page);
        TaskController controller = new TaskController(service, mock(TaskReviewService.class), new ObjectMapper());

        Map<String, Object> response = controller.history(0, 20, "COMPLETED", "SUMMARY", null, null, authentication(17L));

        assertEquals(1L, response.get("total"));
        Map<?, ?> item = (Map<?, ?>) ((List<?>) response.get("items")).get(0);
        assertEquals(Map.of("measureColumn", "Amount"), item.get("workflowOptions"));
        assertEquals(Map.of("counts", Map.of("groupCount", 2)), item.get("resultSummary"));
        assertEquals(List.of(Map.of("fileId", 3L, "name", "sales.xlsx")), item.get("inputFiles"));
        verify(service).history(17L, 0, 20, "COMPLETED", "SUMMARY", null, null);
    }
    @Test
    void reviewEndpointUsesAuthenticatedOwnerAndPageArguments() {
        TaskReviewService reviews = mock(TaskReviewService.class);
        var expected = new TaskReviewService.ReviewPage(2, 1, 10, 14, 90L, null, List.of());
        when(reviews.getReview(81L, 17L, 1, 10)).thenReturn(expected);
        TaskController controller = new TaskController(mock(TaskService.class), reviews, new ObjectMapper());

        var response = controller.review(81L, 1, 10, authentication(17L));

        assertSame(expected, response);
        verify(reviews).getReview(81L, 17L, 1, 10);
    }

    @Test
    void reviewSubmissionUsesAuthenticatedOwner() {
        TaskReviewService reviews = mock(TaskReviewService.class);
        var request = new TaskReviewService.ReviewRequest(3, List.of());
        var expected = new TaskReviewService.ReviewPage(4, 0, 50, 0, 90L, 91L, List.of());
        when(reviews.submitReview(81L, 17L, request)).thenReturn(expected);
        TaskController controller = new TaskController(mock(TaskService.class), reviews, new ObjectMapper());

        assertSame(expected, controller.submitReview(81L, request, authentication(17L)));
        verify(reviews).submitReview(81L, 17L, request);
    }

    private Authentication authentication(Long userId) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userId);
        return authentication;
    }
}
