package com.excelai.task;

import com.excelai.common.ApiException;
import com.excelai.excel.ExcelService;
import com.excelai.excel.ExcelWorkbook;
import com.excelai.excel.DataQualityService;
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
import java.time.LocalDate;
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
        redis = mock(TaskRedisService.class);
        excel = mock(ExcelService.class);
        storage = mock(FileStorageService.class);
        when(users.findById(USER_ID)).thenReturn(new UserRepository.User(USER_ID, "user@example.com", "hash", "FREE"));
        when(plans.findByCode("FREE")).thenReturn(new PlanRepository.Plan("FREE", "Free", 20, 10, 8, 8000));
        when(files.findOwned(anyLong(), eq(USER_ID))).thenAnswer(call -> file(call.getArgument(0), 1));
        when(storage.path(anyString())).thenReturn(Path.of("input.xlsx"));
        when(excel.inspectWorkbook(any())).thenReturn(List.of(new ExcelWorkbook.SheetSchema("Sheet1", List.of("sku", "amount"))));
        when(excel.readWorkbook(any())).thenReturn(new ExcelWorkbook(List.of(new ExcelWorkbook.Sheet("Sheet1", List.of("sku", "amount"), List.of(List.of("A-1", "10"))))));
        doAnswer(call -> {
            previewDigests.put(call.getArgument(0) + ":" + call.getArgument(1), call.getArgument(2));
            return null;
        }).when(redis).putPreview(anyLong(), anyString(), anyString());
        when(redis.getPreview(anyLong(), anyString())).thenAnswer(call ->
                previewDigests.get(call.getArgument(0) + ":" + call.getArgument(1)));
        service = new TaskService(tasks, files, users, plans, usage, redis, excel, storage, new DataQualityService(), new ObjectMapper());
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
        when(tasks.create(USER_ID, List.of(1L), "FREEFORM", "{\"outputFormat\":\"XLSX\"}", "sort this")).thenReturn(42L);

        Long taskId = service.create(USER_ID, 1L, "sort this");

        assertEquals(42L, taskId);
        verify(usage).consume(USER_ID, 42L);
    }

    @Test
    void matchingPreviewCreatesTaskWithOrderedTrustedContext() throws Exception {
        Map<String, Object> options = Map.of("dedupe", true);
        Map<String, Object> preview = service.preview(USER_ID, List.of(2L, 1L), "MERGE_CLEAN", options, "combine");
        String fingerprint = (String) preview.get("previewFingerprint");
        String confirmedOptions = new ObjectMapper().writeValueAsString(preview.get("confirmedOptions"));
        when(tasks.create(USER_ID, List.of(2L, 1L), "MERGE_CLEAN", confirmedOptions, "combine")).thenReturn(91L);

        Long taskId = service.create(USER_ID, List.of(2L, 1L), "MERGE_CLEAN",
                (Map<String, Object>) preview.get("confirmedOptions"), "combine", fingerprint);

        assertEquals(91L, taskId);
        verify(usage).consume(USER_ID, 91L);
        verify(redis).deletePreview(USER_ID, fingerprint);
    }

    @Test
    void configuredPreviewBindsHeaderSnapshotAndTemplateIdToConfirmation() {
        Map<String, Object> options = Map.of("workflowTemplateId", "monthly_sales_summary", "sheetName", "Sheet1");

        Map<String, Object> preview = service.preview(USER_ID, List.of(1L), "SUMMARY", options, "summary");

        Map<?, ?> confirmedOptions = (Map<?, ?>) preview.get("confirmedOptions");
        assertEquals("monthly_sales_summary", confirmedOptions.get("workflowTemplateId"));
        Map<?, ?> snapshot = (Map<?, ?>) confirmedOptions.get("schemaSnapshot");
        assertEquals(1, snapshot.get("version"));
        List<?> inputs = (List<?>) snapshot.get("inputs");
        Map<?, ?> input = (Map<?, ?>) inputs.get(0);
        assertEquals(0, input.get("inputIndex"));
        List<?> sheets = (List<?>) input.get("sheets");
        assertEquals(List.of(Map.of("name", "Sheet1", "headers", List.of("sku", "amount"))), sheets);
        assertFalse(snapshot.toString().contains("A-1"));
        assertFalse(snapshot.toString().contains("10"));
    }

    @Test
    void matchingConfirmedOptionsPersistSnapshotAndTamperingIsRejected() throws Exception {
        Map<String, Object> options = Map.of("workflowTemplateId", "monthly_sales_summary", "sheetName", "Sheet1");
        Map<String, Object> preview = service.preview(USER_ID, List.of(1L), "SUMMARY", options, "summary");
        String fingerprint = (String) preview.get("previewFingerprint");
        Map<String, Object> confirmed = new java.util.TreeMap<>((Map<String, Object>) preview.get("confirmedOptions"));
        String serialized = new ObjectMapper().writeValueAsString(confirmed);
        when(tasks.create(USER_ID, List.of(1L), "SUMMARY", serialized, "summary")).thenReturn(92L);

        assertEquals(92L, service.create(USER_ID, List.of(1L), "SUMMARY", confirmed, "summary", fingerprint));

        Map<String, Object> altered = new java.util.TreeMap<>(confirmed);
        altered.put("workflowTemplateId", "order_reconciliation");
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(USER_ID, List.of(1L), "SUMMARY", altered, "summary", fingerprint));

        Map<String, Object> alteredSnapshot = new java.util.TreeMap<>(confirmed);
        alteredSnapshot.put("schemaSnapshot", Map.of("version", 1, "inputs", List.of(Map.of("inputIndex", 0,
                "sheets", List.of(Map.of("name", "Sheet1", "headers", List.of("tampered")))))));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(USER_ID, List.of(1L), "SUMMARY", alteredSnapshot, "summary", fingerprint));
    }

    @Test
    void defaultsOutputFormatToXlsx() {
        Map<String, Object> preview = service.preview(USER_ID, List.of(1L), "SUMMARY", Map.of(), "summary");
        Map<?, ?> summary = (Map<?, ?>) preview.get("operationSummary");
        Map<?, ?> rules = (Map<?, ?>) summary.get("rules");

        assertEquals("XLSX", rules.get("outputFormat"));
    }

    @Test
    void configuredPreviewIncludesQualityReportForSelectedSheet() {
        when(excel.readWorkbook(any())).thenReturn(new ExcelWorkbook(List.of(
                new ExcelWorkbook.Sheet("Selected", List.of("订单日期", "销售额"), List.of(List.of("2026-01-01", "100"), List.of("待确认", "未知"))),
                new ExcelWorkbook.Sheet("Other", List.of("销售额"), List.of(List.of("not selected"))))));

        Map<String, Object> preview = service.preview(USER_ID, List.of(1L), "SUMMARY", Map.of("sheetName", "Selected"), "summary");

        List<?> reports = (List<?>) preview.get("dataQuality");
        assertEquals(1, reports.size());
        Map<?, ?> fileReport = (Map<?, ?>) reports.get(0);
        List<?> sheets = (List<?>) fileReport.get("sheets");
        assertEquals(1, sheets.size());
        DataQualityService.SheetReport sheet = (DataQualityService.SheetReport) sheets.get(0);
        List<DataQualityService.Finding> findings = sheet.findings();
        assertTrue(findings.stream().anyMatch(item -> item.code().equals("INCONSISTENT_DATE_VALUES")));
        assertFalse(preview.toString().contains("待确认"));
        assertFalse(preview.toString().contains("未知"));
    }

    @Test
    void acceptsCsvForSummaryAndMerge() {
        assertDoesNotThrow(() -> service.preview(USER_ID, List.of(1L), "SUMMARY", Map.of("outputFormat", "csv"), "summary"));
        assertDoesNotThrow(() -> service.preview(USER_ID, List.of(1L, 2L), "MERGE_CLEAN", Map.of("outputFormat", "CSV"), "merge"));
    }

    @Test
    void rejectsCsvForReconcileAndFreeform() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.preview(USER_ID, List.of(1L, 2L), "RECONCILE",
                Map.of("outputFormat", "CSV"), "reconcile"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.preview(USER_ID, List.of(1L), "FREEFORM",
                Map.of("outputFormat", "CSV"), "freeform"));
    }

    @Test
    void rejectsCsvOutputForUnsupportedWorkflowAndFingerprintMismatch() {
        Map<String, Object> csvOptions = Map.of("outputFormat", "CSV");
        Map<String, Object> preview = service.preview(USER_ID, List.of(1L), "SUMMARY", csvOptions, "summary");
        String fingerprint = (String) preview.get("previewFingerprint");

        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(USER_ID, List.of(1L), "SUMMARY",
                Map.of("outputFormat", "XLSX"), "summary", fingerprint));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.create(USER_ID, List.of(1L, 2L), "RECONCILE",
                csvOptions, "reconcile", fingerprint));
        verify(tasks, never()).create(anyLong(), anyList(), anyString(), anyString(), anyString());
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

    @Test
    void taskHistoryNormalizesFiltersAndReturnsOnlyRequestedPage() {
        var expected = new TaskRepository.HistoryPage(List.of(), 1, 25, 41);
        when(tasks.history(USER_ID, 1, 25, "COMPLETED", "SUMMARY", null, null)).thenReturn(expected);

        var result = service.history(USER_ID, 1, 25, " completed ", "summary", null, null);

        assertSame(expected, result);
        verify(tasks).history(USER_ID, 1, 25, "COMPLETED", "SUMMARY", null, null);
    }

    @Test
    void taskHistoryRejectsInvalidPagingAndFilters() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.history(USER_ID, -1, 20, null, null, null, null));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.history(USER_ID, 0, 51, null, null, null, null));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.history(USER_ID, 0, 20, "UNKNOWN", null, null, null));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.history(USER_ID, 0, 20, null, "UNKNOWN", null, null));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.history(USER_ID, 0, 20, null, null,
                LocalDate.parse("2026-10-20"), LocalDate.parse("2026-10-19")));
        verifyNoInteractions(tasks);
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
