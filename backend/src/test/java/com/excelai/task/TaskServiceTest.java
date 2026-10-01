package com.excelai.task;

import com.excelai.agent.AgentService;
import com.excelai.common.ApiException;
import com.excelai.excel.ExcelService;
import com.excelai.excel.ExcelWorkbook;
import com.excelai.file.FileRepository;
import com.excelai.file.FileStorageService;
import com.excelai.plan.PlanRepository;
import com.excelai.usage.UsageService;
import com.excelai.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskServiceTest {
    private static final Long USER_ID = 17L;
    private TaskRepository tasks;
    private FileRepository files;
    private UserRepository users;
    private PlanRepository plans;
    private UsageService usage;
    private TaskWorker worker;
    private TaskRedisService redis;
    private ExcelService excel;
    private FileStorageService storage;
    private TaskService service;
    private final Map<String, String> previewDigests = new java.util.concurrent.ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        tasks = mock(TaskRepository.class);
        files = mock(FileRepository.class);
        users = mock(UserRepository.class);
        plans = mock(PlanRepository.class);
        usage = mock(UsageService.class);
        worker = mock(TaskWorker.class);
        redis = mock(TaskRedisService.class);
        excel = mock(ExcelService.class);
        storage = mock(FileStorageService.class);
        when(users.findById(USER_ID)).thenReturn(new UserRepository.User(USER_ID, "user@example.com", "hash", "FREE"));
        when(plans.findByCode("FREE")).thenReturn(new PlanRepository.Plan("FREE", "Free", 20, 10, 8, 8000));
        when(files.findOwned(anyLong(), eq(USER_ID))).thenAnswer(call -> file(call.getArgument(0), 1));
        when(storage.path(anyString())).thenReturn(Path.of("input.xlsx"));
        when(excel.inspectWorkbook(any())).thenReturn(List.of(new ExcelWorkbook.SheetSchema("Sheet1", List.of("sku", "amount"))));
        doAnswer(call -> {
            previewDigests.put(call.getArgument(0) + ":" + call.getArgument(1), call.getArgument(2));
            return null;
        }).when(redis).putPreview(anyLong(), anyString(), anyString());
        when(redis.getPreview(anyLong(), anyString())).thenAnswer(call ->
                previewDigests.get(call.getArgument(0) + ":" + call.getArgument(1)));
        service = new TaskService(tasks, files, users, plans, usage, worker, redis, excel, storage, new ObjectMapper());
    }

    @Test
    void exposesTypedWorkflowPreview() {
        assertNotNull(service.getClass().getDeclaredMethods());
        assertTrue(java.util.Arrays.stream(service.getClass().getDeclaredMethods()).anyMatch(method -> method.getName().equals("preview")));
    }

    @Test
    void previewRejectsEmptyInputList() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.preview(USER_ID, List.of(), "SUMMARY", Map.of(), "summary"));
    }

    @Test
    void previewRejectsDuplicateInputIds() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.preview(USER_ID, List.of(1L, 1L), "MERGE_CLEAN", Map.of(), "merge"));
    }

    @Test
    void previewRejectsInvalidWorkflowCardinality() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.preview(USER_ID, List.of(1L), "MERGE_CLEAN", Map.of(), "merge"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.preview(USER_ID, List.of(1L, 2L, 3L), "RECONCILE", Map.of(), "reconcile"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.preview(USER_ID, List.of(1L, 2L), "SUMMARY", Map.of(), "summary"));
    }

    @Test
    void previewRejectsAnyUnownedInput() {
        when(files.findOwned(2L, USER_ID)).thenReturn(null);
        assertStatus(HttpStatus.NOT_FOUND,
                () -> service.preview(USER_ID, List.of(1L, 2L), "MERGE_CLEAN", Map.of(), "merge"));
        verify(tasks, never()).create(anyLong(), anyList(), anyString(), anyString(), anyString());
    }

    @Test
    void previewEnforcesPerFileAndAggregateSizeLimits() {
        when(files.findOwned(1L, USER_ID)).thenReturn(file(1L, 11));
        assertStatus(HttpStatus.PAYLOAD_TOO_LARGE,
                () -> service.preview(USER_ID, List.of(1L, 2L), "MERGE_CLEAN", Map.of(), "merge"));

        for (long id = 1; id <= 4; id++) when(files.findOwned(id, USER_ID)).thenReturn(file(id, 10));
        assertStatus(HttpStatus.PAYLOAD_TOO_LARGE,
                () -> service.preview(USER_ID, List.of(1L, 2L, 3L, 4L), "MERGE_CLEAN", Map.of(), "merge"));
    }

    @Test
    void legacyOneFileRequestCreatesFreeformTask() {
        when(tasks.create(USER_ID, List.of(1L), "FREEFORM", "{}", "sort this")).thenReturn(42L);

        Long taskId = service.create(USER_ID, 1L, "sort this");

        assertEquals(42L, taskId);
        verify(worker).submit(42L, USER_ID, List.of(1L), "FREEFORM", Map.of(), "sort this", 8);
    }

    @Test
    void matchingPreviewCreatesTaskWithOrderedTrustedContext() {
        Map<String, Object> options = Map.of("dedupe", true);
        Map<String, Object> preview = service.preview(USER_ID, List.of(2L, 1L), "MERGE_CLEAN", options, "combine");
        String fingerprint = (String) preview.get("previewFingerprint");
        when(tasks.create(USER_ID, List.of(2L, 1L), "MERGE_CLEAN", "{\"dedupe\":true}", "combine")).thenReturn(91L);

        Long taskId = service.create(USER_ID, List.of(2L, 1L), "MERGE_CLEAN", options, "combine", fingerprint);

        assertEquals(91L, taskId);
        verify(worker).submit(91L, USER_ID, List.of(2L, 1L), "MERGE_CLEAN", options, "combine", 8);
        verify(redis).deletePreview(USER_ID, fingerprint);
    }
    @Test
    void createRejectsPreviewFingerprintMismatch() {
        Map<String, Object> preview = service.preview(USER_ID, List.of(1L), "SUMMARY", Map.of("measure", "amount"), "total");
        String fingerprint = (String) preview.get("previewFingerprint");
        when(redis.getPreview(USER_ID, fingerprint)).thenReturn("wrong-digest");

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(USER_ID, List.of(1L), "SUMMARY",
                Map.of("measure", "amount"), "total", fingerprint));
        verify(tasks, never()).create(anyLong(), anyList(), anyString(), anyString(), anyString());
    }

    private FileRepository.FileRecord file(Long id, long size) {
        return new FileRepository.FileRecord(id, USER_ID, "file-" + id + ".xlsx", "stored-" + id,
                "input.xlsx", size, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "INPUT");
    }

    private void assertStatus(HttpStatus expected, Runnable action) {
        ApiException exception = assertThrows(ApiException.class, action::run);
        assertEquals(expected, exception.status());
    }
}