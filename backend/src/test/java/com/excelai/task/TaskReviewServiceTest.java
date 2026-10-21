package com.excelai.task;

import com.excelai.common.ApiException;
import com.excelai.excel.CsvService;
import com.excelai.excel.ExcelService;
import com.excelai.excel.ExcelWorkbook;
import com.excelai.excel.ExcelWorkflowService;
import com.excelai.excel.OutputSheet;
import com.excelai.file.FileRepository;
import com.excelai.file.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskReviewServiceTest {
    @TempDir
    Path tempDir;
    private TaskRepository tasks;
    private FileRepository files;
    private FileStorageService storage;
    private ExcelService excel;
    private TaskReviewService service;

    @BeforeEach
    void setUp() {
        tasks = mock(TaskRepository.class);
        files = mock(FileRepository.class);
        storage = mock(FileStorageService.class);
        excel = mock(ExcelService.class);
        service = new TaskReviewService(tasks, files, storage, excel, new CsvService(),
                new ExcelWorkflowService(), new ObjectMapper());
    }

    @Test
    void reviewGroupsSupportedIssuesAndShowsSourceAndProcessedRows() {
        var options = "{\"sheetNames\":[\"Data\"],\"headerAliases\":{\"SKU\":[\"SKU\"],\"Date\":[\"Date\"],\"Amount\":[\"Amount\"]},\"dateFormats\":{\"Date\":\"yyyy-MM-dd\"},\"amountColumns\":[\"Amount\"]}";
        when(tasks.owned(1L, 7L)).thenReturn(task(options, "{}", "COMPLETED", "MERGE_CLEAN"));
        when(tasks.inputFileIds(1L)).thenReturn(List.of(10L));
        when(files.findOwned(10L, 7L)).thenReturn(file(10L, "input.xlsx", "input-path", "INPUT"));
        when(files.findOwned(90L, 7L)).thenReturn(file(90L, "result.xlsx", "result-path", "OUTPUT"));
        when(storage.path("input-path")).thenReturn(Path.of("input.xlsx"));
        when(storage.path("result-path")).thenReturn(Path.of("result.xlsx"));
        when(excel.readWorkbook(Path.of("input.xlsx"))).thenReturn(new ExcelWorkbook(List.of(
                new ExcelWorkbook.Sheet("Data", List.of("SKU", "Date", "Amount"),
                        List.of(List.of("A", "2026-01-01", "1"), List.of("B", "bad-date", "$oops"))))));
        when(excel.readWorkbook(Path.of("result.xlsx"))).thenReturn(new ExcelWorkbook(List.of(
                new ExcelWorkbook.Sheet("merged_data", List.of("SKU", "Date", "Amount"),
                        List.of(List.of("A", "2026-01-01", "1"), List.of("B", "bad-date", "$oops"))))));

        var page = service.getReview(1L, 7L, 0, 50);

        assertEquals(1, page.total());
        var item = page.items().get(0);
        assertEquals(10L, item.row().fileId());
        assertEquals(3, item.row().rowNumber());
        assertEquals(List.of("B", "bad-date", "$oops"), item.sourceValues());
        assertEquals(List.of("B", "bad-date", "$oops"), item.processedValues());
        assertTrue(item.includedInOriginal());
        assertEquals(List.of("INVALID_AMOUNT", "INVALID_DATE"), item.issues().stream().map(issue -> issue.code()).sorted().toList());
        assertEquals(List.of("SKU", "Date", "Amount"), item.sourceHeaders());
        assertEquals(List.of("SKU", "Date", "Amount"), item.processedHeaders());
    }

    @Test
    void reviewPaginatesGroupedRowsAndUsesStableSourceRowNumbers() {
        var options = "{\"sheetNames\":[\"Data\"],\"headerAliases\":{\"SKU\":[\"SKU\"],\"Date\":[\"Date\"]},\"dateFormats\":{\"Date\":\"yyyy-MM-dd\"}}";
        List<List<String>> rows = java.util.stream.IntStream.range(0, 51)
                .mapToObj(index -> List.of("S" + index, "bad-date" + index)).toList();
        when(tasks.owned(1L, 7L)).thenReturn(task(options, "{}", "COMPLETED", "MERGE_CLEAN"));
        when(tasks.inputFileIds(1L)).thenReturn(List.of(10L));
        when(files.findOwned(10L, 7L)).thenReturn(file(10L, "input.xlsx", "input-path", "INPUT"));
        when(files.findOwned(90L, 7L)).thenReturn(file(90L, "result.xlsx", "result-path", "OUTPUT"));
        when(storage.path("input-path")).thenReturn(Path.of("input.xlsx"));
        when(storage.path("result-path")).thenReturn(Path.of("result.xlsx"));
        when(excel.readWorkbook(Path.of("input.xlsx"))).thenReturn(new ExcelWorkbook(List.of(
                new ExcelWorkbook.Sheet("Data", List.of("SKU", "Date"), rows))));
        when(excel.readWorkbook(Path.of("result.xlsx"))).thenReturn(new ExcelWorkbook(List.of(
                new ExcelWorkbook.Sheet("merged_data", List.of("SKU", "Date"), rows))));

        var page = service.getReview(1L, 7L, 1, 50);

        assertEquals(51, page.total());
        assertEquals(1, page.items().size());
        assertEquals(52, page.items().get(0).row().rowNumber());
        assertEquals(List.of("S50", "bad-date50"), page.items().get(0).sourceValues());
    }

    @Test
    void reviewMarksSkippedDuplicateWithoutOriginalOutputCounterpart() {
        var options = "{\"sheetNames\":[\"Data\"],\"headerAliases\":{\"SKU\":[\"SKU\"],\"Date\":[\"Date\"]},\"duplicateKeyColumns\":[\"SKU\"]}";
        List<List<String>> sourceRows = List.of(List.of("A", "first"), List.of("A", "duplicate"));
        when(tasks.owned(1L, 7L)).thenReturn(task(options, "{}", "COMPLETED", "MERGE_CLEAN"));
        when(tasks.inputFileIds(1L)).thenReturn(List.of(10L));
        when(files.findOwned(10L, 7L)).thenReturn(file(10L, "input.xlsx", "input-path", "INPUT"));
        when(files.findOwned(90L, 7L)).thenReturn(file(90L, "result.xlsx", "result-path", "OUTPUT"));
        when(storage.path("input-path")).thenReturn(Path.of("input.xlsx"));
        when(storage.path("result-path")).thenReturn(Path.of("result.xlsx"));
        when(excel.readWorkbook(Path.of("input.xlsx"))).thenReturn(new ExcelWorkbook(List.of(
                new ExcelWorkbook.Sheet("Data", List.of("SKU", "Date"), sourceRows))));
        when(excel.readWorkbook(Path.of("result.xlsx"))).thenReturn(new ExcelWorkbook(List.of(
                new ExcelWorkbook.Sheet("merged_data", List.of("SKU", "Date"), List.of(sourceRows.get(0))))));

        var page = service.getReview(1L, 7L, 0, 50);

        assertEquals(1, page.total());
        var duplicate = page.items().get(0);
        assertEquals(3, duplicate.row().rowNumber());
        assertFalse(duplicate.includedInOriginal());
        assertTrue(duplicate.processedValues().isEmpty());
        assertEquals("DUPLICATE_ROW_SKIPPED", duplicate.issues().get(0).code());
    }

    @Test
    void reviewRejectsNonOwnedTask() {
        when(tasks.owned(1L, 7L)).thenReturn(null);

        ApiException exception = assertThrows(ApiException.class, () -> service.getReview(1L, 7L, 0, 50));

        assertEquals(HttpStatus.NOT_FOUND, exception.status());
        verifyNoInteractions(files, storage, excel);
    }

    @Test
    void reviewRejectsTasksThatAreNotCompletedMergeWorkflows() {
        when(tasks.owned(1L, 7L)).thenReturn(task("{}", "{}", "RUNNING", "MERGE_CLEAN"));

        assertThrows(ApiException.class, () -> service.getReview(1L, 7L, 0, 50));
        verifyNoInteractions(files, storage, excel);
    }

    @Test
    void submitRejectsStaleReviewVersionBeforeReadingWorkbookFiles() {
        when(tasks.owned(1L, 7L)).thenReturn(task("{}", "{\"review\":{\"version\":2}}", "COMPLETED", "MERGE_CLEAN"));
        var request = new TaskReviewService.ReviewRequest(1, List.of());

        ApiException exception = assertThrows(ApiException.class, () -> service.submitReview(1L, 7L, request));

        assertEquals(HttpStatus.CONFLICT, exception.status());
        verifyNoInteractions(files, storage, excel);
    }

    @Test
    void validCorrectionCreatesSeparateRevisionAndRetryReusesIt() throws Exception {
        SubmitFixture fixture = submitFixture(true, false);
        var request = correctionRequest("2026-01-02");

        var first = fixture.service().submitReview(1L, 7L, request);
        var retry = fixture.service().submitReview(1L, 7L, request);

        assertEquals(1, first.version());
        assertEquals(90L, first.originalFileId());
        assertEquals(91L, first.revisedFileId());
        assertEquals(first.revisedFileId(), retry.revisedFileId());
        assertEquals("2026-01-02", new CsvService().read(fixture.revisedPath()).rows().get(0).get(1));
        assertEquals("bad-date", new CsvService().read(fixture.originalPath()).rows().get(0).get(1));
        verify(fixture.files(), times(1)).insert(eq(7L), anyString(), anyString(), anyString(), anyLong(), anyString(), eq("OUTPUT"));
        verify(fixture.tasks(), times(1)).updateSummaryIfUnchanged(eq(1L), eq("{}"), anyString());
    }

    @Test
    void invalidCorrectionDoesNotCreateOrPersistRevision() throws Exception {
        SubmitFixture fixture = submitFixture(true, false);

        assertThrows(IllegalArgumentException.class,
                () -> fixture.service().submitReview(1L, 7L, correctionRequest("not-a-date")));

        assertFalse(Files.exists(fixture.revisedPath()));
        verify(fixture.files(), never()).insert(eq(7L), anyString(), anyString(), anyString(), anyLong(), anyString(), anyString());
        verify(fixture.tasks(), never()).updateSummaryIfUnchanged(any(), any(), any());
    }

    @Test
    void skipDecisionExportsRevisionWithoutTheReviewedRow() throws Exception {
        SubmitFixture fixture = submitFixture(true, false);
        var request = new TaskReviewService.ReviewRequest(0, List.of(new TaskReviewService.ReviewDecisionInput(
                new com.excelai.excel.MergeReviewDecision.SourceRowKey(10L, "csv_data", 2),
                com.excelai.excel.MergeReviewDecision.Action.SKIP, java.util.Map.of())));

        var result = fixture.service().submitReview(1L, 7L, request);

        assertEquals(1, result.version());
        assertTrue(new CsvService().read(fixture.revisedPath()).rows().isEmpty());
        assertEquals("bad-date", new CsvService().read(fixture.originalPath()).rows().get(0).get(1));
    }

    @Test
    void compareAndSetConflictDeletesUnlinkedRevisionFile() throws Exception {
        SubmitFixture fixture = submitFixture(false, false);

        ApiException exception = assertThrows(ApiException.class,
                () -> fixture.service().submitReview(1L, 7L, correctionRequest("2026-01-02")));

        assertEquals(HttpStatus.CONFLICT, exception.status());
        verify(fixture.files()).deleteOwned(91L, 7L);
        verify(fixture.storage()).delete(fixture.revisedPath().toString());
        assertFalse(Files.exists(fixture.revisedPath()));
        verify(fixture.tasks()).updateSummaryIfUnchanged(eq(1L), eq("{}"), anyString());
    }

    @Test
    void fileRecordFailureDeletesWrittenRevisionFile() throws Exception {
        SubmitFixture fixture = submitFixture(true, true);

        ApiException exception = assertThrows(ApiException.class,
                () -> fixture.service().submitReview(1L, 7L, correctionRequest("2026-01-02")));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.status());
        verify(fixture.storage()).delete(fixture.revisedPath().toString());
        assertFalse(Files.exists(fixture.revisedPath()));
        verify(fixture.tasks(), never()).updateSummaryIfUnchanged(any(), any(), any());
    }

    private SubmitFixture submitFixture(boolean compareAndSetSucceeds, boolean insertFails) throws Exception {
        Path inputPath = tempDir.resolve("input.csv");
        Path originalPath = tempDir.resolve("original.csv");
        Path revisedPath = tempDir.resolve("revision.csv");
        CsvService csvService = new CsvService();
        OutputSheet inputSheet = new OutputSheet("csv_data", List.of("SKU", "Date"), List.of(List.of("A", "bad-date")));
        csvService.write(inputPath, inputSheet);
        csvService.write(originalPath, inputSheet);

        TaskRepository taskRepository = mock(TaskRepository.class);
        FileRepository fileRepository = mock(FileRepository.class);
        FileStorageService fileStorage = mock(FileStorageService.class);
        AtomicReference<String> summary = new AtomicReference<>("{}");
        String options = "{\"sheetNames\":[\"csv_data\"],\"headerAliases\":{\"SKU\":[\"SKU\"],\"Date\":[\"Date\"]},\"dateFormats\":{\"Date\":\"yyyy-MM-dd\"},\"outputFormat\":\"CSV\"}";
        when(taskRepository.owned(1L, 7L)).thenAnswer(invocation -> task(options, summary.get(), "COMPLETED", "MERGE_CLEAN"));
        when(taskRepository.inputFileIds(1L)).thenReturn(List.of(10L));
        when(taskRepository.updateSummaryIfUnchanged(eq(1L), any(), any())).thenAnswer(invocation -> {
            if (!compareAndSetSucceeds || !java.util.Objects.equals(summary.get(), invocation.getArgument(1))) return false;
            summary.set(invocation.getArgument(2));
            return true;
        });
        when(fileRepository.findOwned(10L, 7L)).thenReturn(file(10L, "input.csv", "input-path", "INPUT"));
        when(fileRepository.findOwned(90L, 7L)).thenReturn(file(90L, "original.csv", "original-path", "OUTPUT"));
        if (insertFails) {
            when(fileRepository.insert(eq(7L), anyString(), anyString(), anyString(), anyLong(), anyString(), eq("OUTPUT")))
                    .thenThrow(new IllegalStateException("metadata insert failed"));
        } else {
            when(fileRepository.insert(eq(7L), anyString(), anyString(), anyString(), anyLong(), anyString(), eq("OUTPUT")))
                    .thenReturn(91L);
        }
        when(fileStorage.path("input-path")).thenReturn(inputPath);
        when(fileStorage.path("original-path")).thenReturn(originalPath);
        when(fileStorage.savePathName(anyString())).thenReturn(revisedPath);
        doAnswer(invocation -> Files.deleteIfExists(Path.of((String) invocation.getArgument(0))))
                .when(fileStorage).delete(anyString());
        TaskReviewService reviewService = new TaskReviewService(taskRepository, fileRepository, fileStorage,
                new ExcelService(csvService), csvService, new ExcelWorkflowService(), new ObjectMapper());
        return new SubmitFixture(reviewService, taskRepository, fileRepository, fileStorage, revisedPath, originalPath);
    }

    private TaskReviewService.ReviewRequest correctionRequest(String date) {
        return new TaskReviewService.ReviewRequest(0, List.of(new TaskReviewService.ReviewDecisionInput(
                new com.excelai.excel.MergeReviewDecision.SourceRowKey(10L, "csv_data", 2),
                com.excelai.excel.MergeReviewDecision.Action.CORRECT, java.util.Map.of("Date", date))));
    }

    private record SubmitFixture(TaskReviewService service, TaskRepository tasks, FileRepository files, FileStorageService storage,
                                 Path revisedPath, Path originalPath) { }

    private TaskRepository.Task task(String options, String summary, String status, String workflow) {
        return new TaskRepository.Task(1L, 7L, 10L, "merge", workflow, options, status, 100, 90L, null, summary);
    }

    private FileRepository.FileRecord file(Long id, String name, String path, String role) {
        return new FileRepository.FileRecord(id, 7L, name, name, path, 1,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", role);
    }
}
