package com.excelai.task;

import com.excelai.common.ApiException;
import com.excelai.excel.CsvService;
import com.excelai.excel.ExcelService;
import com.excelai.excel.ExcelWorkbook;
import com.excelai.excel.ExcelWorkflowService;
import com.excelai.excel.MergeOptions;
import com.excelai.excel.MergeOptionsParser;
import com.excelai.excel.MergeReviewDecision;
import com.excelai.excel.SheetInput;
import com.excelai.excel.WorkflowResult;
import com.excelai.file.FileRepository;
import com.excelai.file.FileStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class TaskReviewService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_PAGE = 10_000;
    private static final Set<String> REVIEWABLE_CODES = Set.of(
            "INVALID_DATE", "INVALID_AMOUNT", "DUPLICATE_ROW_SKIPPED", "BLANK_ROW_SKIPPED");

    private final TaskRepository tasks;
    private final FileRepository files;
    private final FileStorageService storage;
    private final ExcelService excel;
    private final CsvService csv;
    private final ExcelWorkflowService workflows;
    private final ObjectMapper mapper;

    public TaskReviewService(TaskRepository tasks, FileRepository files, FileStorageService storage,
                             ExcelService excel, CsvService csv, ExcelWorkflowService workflows, ObjectMapper mapper) {
        this.tasks = tasks;
        this.files = files;
        this.storage = storage;
        this.excel = excel;
        this.csv = csv;
        this.workflows = workflows;
        this.mapper = mapper;
    }

    public record ReviewRequest(int expectedVersion, List<ReviewDecisionInput> decisions) {
        public ReviewRequest {
            decisions = decisions == null ? List.of() : List.copyOf(decisions);
        }
    }

    public record ReviewDecisionInput(MergeReviewDecision.SourceRowKey row, MergeReviewDecision.Action action,
                                      Map<String, String> corrections) {
        public ReviewDecisionInput {
            corrections = corrections == null ? Map.of() : Map.copyOf(corrections);
        }
    }

    public record ReviewPage(int version, int page, int size, long total, Long originalFileId,
                             Long revisedFileId, List<ReviewItem> items) { }

    public record ReviewItem(MergeReviewDecision.SourceRowKey row, String fileName, String sheetName,
                             List<String> sourceHeaders, List<String> processedHeaders,
                             List<String> sourceValues, List<String> processedValues,
                             boolean includedInOriginal, List<WorkflowResult.RowException> issues,
                             MergeReviewDecision.Decision decision) { }

    public ReviewPage getReview(Long taskId, Long userId, int page, int size) {
        if (page < 0 || page > MAX_PAGE || size < 1 || size > MAX_PAGE_SIZE)
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid review page or size");
        TaskRepository.Task task = requireReviewableTask(taskId, userId);
        if (task.resultFileId() == null) throw new ApiException(HttpStatus.CONFLICT, "completed task has no original result file");

        Map<String, Object> options = parseOptions(task.workflowOptions());
        MergeOptions mergeOptions = MergeOptionsParser.parse(options);
        List<SheetInput> inputs = readInputs(taskId, userId, options);
        WorkflowResult original = workflows.mergeAndClean(inputs, mergeOptions);
        var originalFile = files.findOwned(task.resultFileId(), userId);
        if (originalFile == null) throw new ApiException(HttpStatus.NOT_FOUND, "original result file not found");
        ExcelWorkbook.Sheet outputSheet = readOutputSheet(originalFile);

        Map<MergeReviewDecision.SourceRowKey, List<WorkflowResult.RowException>> grouped = new LinkedHashMap<>();
        for (WorkflowResult.RowException issue : original.exceptions()) {
            if (issue.rowNumber() >= 2 && REVIEWABLE_CODES.contains(issue.code())) {
                grouped.computeIfAbsent(rowKey(issue), ignored -> new ArrayList<>()).add(issue);
            }
        }

        Map<MergeReviewDecision.SourceRowKey, MergeReviewDecision.Decision> decisions = readDecisions(task.resultSummary());
        List<Map.Entry<MergeReviewDecision.SourceRowKey, List<WorkflowResult.RowException>>> entries =
                new ArrayList<>(grouped.entrySet());
        int start = (int) Math.min((long) page * size, entries.size());
        int end = Math.min(start + size, entries.size());
        List<Map.Entry<MergeReviewDecision.SourceRowKey, List<WorkflowResult.RowException>>> selected =
                entries.subList(start, end);
        Set<MergeReviewDecision.SourceRowKey> selectedKeys = new LinkedHashSet<>();
        selected.forEach(entry -> selectedKeys.add(entry.getKey()));
        Map<MergeReviewDecision.SourceRowKey, SheetInput> inputByRow = indexInputRows(inputs, selectedKeys);
        Map<Long, String> inputNames = new LinkedHashMap<>();
        for (Long fileId : selectedKeys.stream().map(MergeReviewDecision.SourceRowKey::fileId).distinct().toList()) {
            var file = files.findOwned(fileId, userId);
            if (file == null) throw new ApiException(HttpStatus.NOT_FOUND, "task input file not found");
            inputNames.put(fileId, file.originalName());
        }
        List<ReviewItem> items = new ArrayList<>();
        for (var entry : selected) {
            SheetInput input = inputByRow.get(entry.getKey());
            int rowIndex = entry.getKey().rowNumber() - 2;
            List<String> sourceValues = input.rows().get(rowIndex);
            boolean skippedByOriginal = entry.getValue().stream().anyMatch(issue ->
                    issue.code().equals("DUPLICATE_ROW_SKIPPED") || issue.code().equals("BLANK_ROW_SKIPPED"));
            List<String> processedValues = processedRow(input, sourceValues, mergeOptions);
            boolean includedInOriginal = !skippedByOriginal && outputSheet.rows().contains(processedValues);
            items.add(new ReviewItem(entry.getKey(), inputNames.get(entry.getKey().fileId()), input.sheetName(),
                    input.headers(), outputSheet.headers(), sourceValues,
                    includedInOriginal ? processedValues : List.of(), includedInOriginal,
                    List.copyOf(entry.getValue()), decisions.get(entry.getKey())));
        }

        Map<String, Object> summary = parseSummary(task.resultSummary());
        Map<?, ?> review = asMap(summary.get("review"));
        return new ReviewPage(number(review.get("version"), 0), page, size, entries.size(), task.resultFileId(),
                nullableNumber(review.get("revisedFileId")), List.copyOf(items));
    }

    public ReviewPage submitReview(Long taskId, Long userId, ReviewRequest request) {
        if (request == null || request.expectedVersion() < 0)
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid review request");
        TaskRepository.Task task = requireReviewableTask(taskId, userId);
        if (task.resultFileId() == null) throw new ApiException(HttpStatus.CONFLICT, "completed task has no original result file");
        Map<String, Object> summary = parseSummary(task.resultSummary());
        Map<?, ?> savedReview = asMap(summary.get("review"));
        int currentVersion = number(savedReview.get("version"), 0);
        Map<MergeReviewDecision.SourceRowKey, MergeReviewDecision.Decision> requested = toDecisionMap(request);
        String digest = digest(requested);
        if (request.expectedVersion() + 1 == currentVersion && digest.equals(savedReview.get("requestDigest"))) {
            return getReview(taskId, userId, 0, MAX_PAGE_SIZE);
        }
        if (request.expectedVersion() != currentVersion)
            throw new ApiException(HttpStatus.CONFLICT, "review version is stale");

        Map<String, Object> options = parseOptions(task.workflowOptions());
        MergeOptions mergeOptions = MergeOptionsParser.parse(options);
        List<SheetInput> inputs = readInputs(taskId, userId, options);
        WorkflowResult baseline = workflows.mergeAndClean(inputs, mergeOptions);
        validateDecisionKeys(requested, baseline);
        WorkflowResult revised = workflows.mergeAndClean(inputs, mergeOptions, requested);
        var originalFile = files.findOwned(task.resultFileId(), userId);
        if (originalFile == null) throw new ApiException(HttpStatus.NOT_FOUND, "original result file not found");

        String oldSummary = task.resultSummary();
        Long revisedFileId = null;
        String revisedPath = null;
        try {
            boolean csvOutput = outputIsCsv(originalFile, options);
            String extension = csvOutput ? ".csv" : ".xlsx";
            String name = "ai-merge-clean-review-" + taskId + "-v" + (currentVersion + 1) + "-" + UUID.randomUUID() + extension;
            Path path = storage.savePathName(name);
            revisedPath = path.toString();
            if (csvOutput) {
                if (revised.outputSheets().size() != 1) throw new ApiException(HttpStatus.CONFLICT, "revised CSV requires one output worksheet");
                csv.write(path, revised.outputSheets().get(0));
            } else {
                excel.writeWorkbook(path, revised.outputSheets());
            }
            String contentType = csvOutput ? "text/csv; charset=UTF-8"
                    : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            revisedFileId = files.insert(userId, name, path.getFileName().toString(), path.toString(), Files.size(path), contentType, "OUTPUT");

            Map<String, Object> review = new LinkedHashMap<>();
            review.put("version", currentVersion + 1);
            review.put("decisions", requested.entrySet().stream().map(entry -> decisionJson(entry.getKey(), entry.getValue())).toList());
            review.put("requestDigest", digest);
            review.put("revisedFileId", revisedFileId);
            review.put("updatedAt", LocalDateTime.now().toString());
            summary.put("review", review);
            String updatedSummary = mapper.writeValueAsString(summary);
            if (!tasks.updateSummaryIfUnchanged(taskId, oldSummary, updatedSummary)) {
                cleanupRevision(revisedFileId, userId, revisedPath);
                throw new ApiException(HttpStatus.CONFLICT, "review changed while revision was being generated");
            }
            return getReview(taskId, userId, 0, MAX_PAGE_SIZE);
        } catch (ApiException e) {
            if (revisedFileId == null && revisedPath != null) deletePath(revisedPath);
            throw e;
        } catch (Exception e) {
            cleanupRevision(revisedFileId, userId, revisedPath);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "could not create revised result");
        }
    }

    private Map<MergeReviewDecision.SourceRowKey, MergeReviewDecision.Decision> toDecisionMap(ReviewRequest request) {
        if (request == null || request.expectedVersion() < 0) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid review request");
        Map<MergeReviewDecision.SourceRowKey, MergeReviewDecision.Decision> result = new LinkedHashMap<>();
        for (ReviewDecisionInput input : request.decisions()) {
            if (input.row() == null || input.action() == null) throw new ApiException(HttpStatus.BAD_REQUEST, "review row and action are required");
            if (result.putIfAbsent(input.row(), new MergeReviewDecision.Decision(input.action(), input.corrections())) != null)
                throw new ApiException(HttpStatus.BAD_REQUEST, "duplicate review row decision");
        }
        return Map.copyOf(result);
    }

    private void validateDecisionKeys(Map<MergeReviewDecision.SourceRowKey, MergeReviewDecision.Decision> decisions,
                                      WorkflowResult baseline) {
        Set<MergeReviewDecision.SourceRowKey> known = new LinkedHashSet<>();
        baseline.exceptions().stream().filter(issue -> issue.rowNumber() >= 2 && REVIEWABLE_CODES.contains(issue.code()))
                .forEach(issue -> known.add(rowKey(issue)));
        if (!known.containsAll(decisions.keySet())) throw new ApiException(HttpStatus.CONFLICT, "review contains an unknown or stale source row");
    }

    private boolean outputIsCsv(FileRepository.FileRecord originalFile, Map<String, Object> options) {
        String configured = Objects.toString(options.get("outputFormat"), "").toUpperCase(Locale.ROOT);
        if (configured.equals("CSV")) return true;
        if (configured.equals("XLSX")) return false;
        String name = originalFile.storedName() == null ? originalFile.originalName() : originalFile.storedName();
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".csv");
    }

    private String digest(Map<MergeReviewDecision.SourceRowKey, MergeReviewDecision.Decision> decisions) {
        try {
            List<Map<String, Object>> canonical = decisions.entrySet().stream()
                    .sorted(java.util.Comparator.comparing((Map.Entry<MergeReviewDecision.SourceRowKey, MergeReviewDecision.Decision> e) -> e.getKey().fileId())
                            .thenComparing(e -> e.getKey().sheetName()).thenComparingInt(e -> e.getKey().rowNumber()))
                    .map(entry -> decisionJson(entry.getKey(), entry.getValue())).toList();
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(canonical));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "could not fingerprint review request");
        }
    }

    private Map<String, Object> decisionJson(MergeReviewDecision.SourceRowKey key, MergeReviewDecision.Decision decision) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("fileId", key.fileId());
        row.put("sheetName", key.sheetName());
        row.put("rowNumber", key.rowNumber());
        Map<String, String> corrections = new java.util.TreeMap<>(decision.corrections());
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("row", row);
        value.put("action", decision.action().name());
        value.put("corrections", corrections);
        return value;
    }

    private void cleanupRevision(Long fileId, Long userId, String path) {
        try {
            if (fileId != null) files.deleteOwned(fileId, userId);
        } finally {
            deletePath(path);
        }
    }

    private void deletePath(String path) {
        if (path == null) return;
        try { storage.delete(path); } catch (Exception ignored) { }
    }

    private TaskRepository.Task requireReviewableTask(Long taskId, Long userId) {
        TaskRepository.Task task = tasks.owned(taskId, userId);
        if (task == null) throw new ApiException(HttpStatus.NOT_FOUND, "task not found");
        if (!"COMPLETED".equals(task.status())) throw new ApiException(HttpStatus.BAD_REQUEST, "only completed tasks can be reviewed");
        if (!"MERGE_CLEAN".equals(task.workflowType())) throw new ApiException(HttpStatus.BAD_REQUEST, "review is supported only for merge-and-clean tasks");
        return task;
    }

    private List<SheetInput> readInputs(Long taskId, Long userId, Map<String, Object> options) {
        List<Long> ids = tasks.inputFileIds(taskId);
        List<?> selectedNames = options.get("sheetNames") instanceof List<?> list ? list : List.of();
        if (ids.isEmpty() || (!selectedNames.isEmpty() && selectedNames.size() != ids.size()))
            throw new ApiException(HttpStatus.CONFLICT, "original task inputs are unavailable");
        List<SheetInput> result = new ArrayList<>();
        for (int index = 0; index < ids.size(); index++) {
            Long fileId = ids.get(index);
            var file = files.findOwned(fileId, userId);
            if (file == null) throw new ApiException(HttpStatus.NOT_FOUND, "task input file not found");
            ExcelWorkbook workbook;
            try {
                workbook = excel.readWorkbook(storage.path(file.storagePath()));
            } catch (RuntimeException e) {
                throw new ApiException(HttpStatus.CONFLICT, "original task input cannot be read");
            }
            String selectedName = selectedNames.size() > index ? String.valueOf(selectedNames.get(index)) : null;
            ExcelWorkbook.Sheet selected = chooseSheet(workbook, selectedName);
            result.add(new SheetInput(file.id(), selected.name(), selected.headers(), selected.rows()));
        }
        return List.copyOf(result);
    }

    private ExcelWorkbook.Sheet chooseSheet(ExcelWorkbook workbook, String name) {
        if (name != null && !name.isBlank()) return workbook.sheets().stream().filter(sheet -> sheet.name().equals(name)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "original task worksheet is unavailable"));
        if (workbook.sheets().size() != 1) throw new ApiException(HttpStatus.CONFLICT, "original task worksheet cannot be identified");
        return workbook.sheets().get(0);
    }

    private ExcelWorkbook.Sheet readOutputSheet(FileRepository.FileRecord output) {
        Path path = storage.path(output.storagePath());
        String name = output.storedName() == null ? output.originalName() : output.storedName();
        try {
            if (name != null && name.toLowerCase(Locale.ROOT).endsWith(".csv")) return csv.read(path);
            ExcelWorkbook workbook = excel.readWorkbook(path);
            return workbook.sheets().stream().filter(sheet -> sheet.name().equals("merged_data")).findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "original merge result worksheet is unavailable"));
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.CONFLICT, "original task result cannot be read");
        }
    }

    private List<String> processedRow(SheetInput input, List<String> sourceRow, MergeOptions options) {
        WorkflowResult rowResult = workflows.mergeAndClean(List.of(new SheetInput(input.fileId(), input.sheetName(),
                input.headers(), List.of(sourceRow))), options);
        return rowResult.outputSheets().get(0).rows().stream().findFirst().orElse(List.of());
    }

    private Map<MergeReviewDecision.SourceRowKey, SheetInput> indexInputRows(
            List<SheetInput> inputs, Set<MergeReviewDecision.SourceRowKey> keys) {
        Map<MergeReviewDecision.SourceRowKey, SheetInput> result = new LinkedHashMap<>();
        for (MergeReviewDecision.SourceRowKey key : keys) {
            SheetInput input = inputs.stream().filter(candidate -> candidate.fileId().equals(key.fileId())
                    && candidate.sheetName().equals(key.sheetName())).findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "issue source row is unavailable"));
            if (key.rowNumber() - 2 >= input.rows().size()) throw new ApiException(HttpStatus.CONFLICT, "issue source row is unavailable");
            result.put(key, input);
        }
        return result;
    }

    private Map<String, Object> parseOptions(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return mapper.readValue(json, new TypeReference<>() { });
        } catch (Exception e) {
            throw new ApiException(HttpStatus.CONFLICT, "stored merge options are unavailable");
        }
    }

    private Map<String, Object> parseSummary(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return mapper.readValue(json, new TypeReference<>() { });
        } catch (Exception e) {
            throw new ApiException(HttpStatus.CONFLICT, "stored task summary is unavailable");
        }
    }

    private Map<MergeReviewDecision.SourceRowKey, MergeReviewDecision.Decision> readDecisions(String summaryJson) {
        Map<?, ?> review = asMap(parseSummary(summaryJson).get("review"));
        Object stored = review.get("decisions");
        if (!(stored instanceof List<?> list)) return Map.of();
        Map<MergeReviewDecision.SourceRowKey, MergeReviewDecision.Decision> result = new LinkedHashMap<>();
        for (Object value : list) {
            Map<?, ?> item = asMap(value);
            Map<?, ?> row = asMap(item.get("row"));
            try {
                var key = new MergeReviewDecision.SourceRowKey(nullableNumber(row.get("fileId")),
                        Objects.toString(row.get("sheetName"), ""), number(row.get("rowNumber"), 0));
                var action = MergeReviewDecision.Action.valueOf(Objects.toString(item.get("action"), ""));
                result.put(key, new MergeReviewDecision.Decision(action, stringMap(item.get("corrections"))));
            } catch (RuntimeException ignored) {
                return Map.of();
            }
        }
        return Map.copyOf(result);
    }

    private Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), String.valueOf(item)));
        return Map.copyOf(result);
    }

    private MergeReviewDecision.SourceRowKey rowKey(WorkflowResult.RowException issue) {
        return new MergeReviewDecision.SourceRowKey(issue.fileId(), issue.sheetName(), issue.rowNumber());
    }

    private Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private int number(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private Long nullableNumber(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
