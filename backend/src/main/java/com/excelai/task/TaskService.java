package com.excelai.task;

import com.excelai.common.ApiException;
import com.excelai.excel.ExcelService;
import com.excelai.excel.DataQualityService;
import com.excelai.excel.ExcelWorkbook;
import com.excelai.file.FileRepository;
import com.excelai.file.FileStorageService;
import com.excelai.plan.PlanRepository;
import com.excelai.usage.UsageService;
import com.excelai.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
/**
 * 中文：同步校验任务输入和套餐限制，再登记并异步派发 Excel 工作流。
 * English: Validates task inputs and plan limits synchronously, then persists and asynchronously dispatches an Excel workflow.
 */
public class TaskService {
    private static final int MAX_INPUT_FILES = 5;
    private static final long AGGREGATE_SIZE_MULTIPLIER = 3;
    private static final int MAX_HISTORY_PAGE = 10_000;
    private static final int MAX_HISTORY_PAGE_SIZE = 50;
    private static final Set<String> HISTORY_STATUSES = Set.of("QUEUED", "RUNNING", "COMPLETED", "FAILED");
    private static final Map<String, WorkflowType> TEMPLATE_WORKFLOWS = Map.of(
            "monthly_sales_summary", WorkflowType.SUMMARY,
            "order_reconciliation", WorkflowType.RECONCILE,
            "multi_file_dedupe_merge", WorkflowType.MERGE_CLEAN);

    private final TaskRepository tasks;
    private final FileRepository files;
    private final UserRepository users;
    private final PlanRepository plans;
    private final UsageService usage;
    private final TaskRedisService redis;
    private final ExcelService excel;
    private final FileStorageService storage;
    private final DataQualityService dataQuality;
    private final ObjectMapper mapper;

    public TaskService(TaskRepository t, FileRepository f, UserRepository u, PlanRepository p,
                       UsageService x, TaskRedisService r, ExcelService e,
                       FileStorageService s, DataQualityService q, ObjectMapper m) {
        tasks = t;
        files = f;
        users = u;
        plans = p;
        usage = x;
        redis = r;
        excel = e;
        storage = s;
        dataQuality = q;
        mapper = m;
    }

    public Map<String, Object> preview(Long uid, List<Long> fileIds, String workflowName,
                                       Map<String, Object> options, String prompt) {
        WorkflowType workflow = parseWorkflow(workflowName);
        List<Long> inputIds = normalizeIds(fileIds);
        validateCardinality(inputIds, workflow);
        Map<String, Object> normalizedOptions = normalizeOptions(workflow, options);
        boolean collectQuality = options != null && !options.isEmpty() && workflow != WorkflowType.FREEFORM;
        normalizedOptions.remove("schemaSnapshot");
        PlanRepository.Plan plan = planFor(uid);
        List<Map<String, Object>> inputSummaries = new ArrayList<>();
        List<Map<String, Object>> qualitySummaries = new ArrayList<>();
        List<Map<String, Object>> snapshotInputs = new ArrayList<>();
        long totalBytes = 0;
        long aggregateLimit = Math.multiplyExact(plan.maxFileSize(), AGGREGATE_SIZE_MULTIPLIER);
        for (Long fileId : inputIds) {
            FileRepository.FileRecord file = ownedFile(fileId, uid);
            validateSize(file, plan);
            totalBytes = Math.addExact(totalBytes, file.sizeBytes());
            if (totalBytes > aggregateLimit) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "combined files exceed plan limit");
            var path = storage.path(file.storagePath());
            ExcelWorkbook fullWorkbook = collectQuality ? excel.readWorkbook(path) : null;
            var workbookSchemas = collectQuality
                    ? fullWorkbook.sheets().stream().map(sheet -> new ExcelWorkbook.SheetSchema(sheet.name(), sheet.headers())).toList()
                    : excel.inspectWorkbook(path);
            List<Map<String, Object>> sheetSummaries = workbookSchemas.stream().map(sheet -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", sheet.name());
                item.put("headers", sheet.headers());
                return item;
            }).toList();
            if (collectQuality) {
                int inputIndex = inputIds.indexOf(fileId);
                List<Map<String, Object>> snapshotSheets = selectedSchemas(workbookSchemas, workflow, normalizedOptions, inputIndex)
                        .stream().map(sheet -> Map.<String, Object>of("name", sheet.name(), "headers", sheet.headers())).toList();
                snapshotInputs.add(Map.of("inputIndex", inputIndex, "sheets", snapshotSheets));
            }
            if (collectQuality) {
                List<ExcelWorkbook.Sheet> selectedSheets = selectedQualitySheets(fullWorkbook, workflow, normalizedOptions, inputIds.indexOf(fileId));
                Map<String, Object> quality = new LinkedHashMap<>();
                quality.put("fileId", file.id());
                quality.put("fileName", file.originalName());
                quality.put("sheets", dataQuality.analyze(new ExcelWorkbook(selectedSheets)).sheets());
                qualitySummaries.add(quality);
            }
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("fileId", file.id());
            input.put("name", file.originalName());
            input.put("sizeBytes", file.sizeBytes());
            input.put("sheets", sheetSummaries);
            inputSummaries.add(input);
        }

        String normalizedPrompt = prompt == null ? "" : prompt.trim();
        if (collectQuality) {
            normalizedOptions.put("schemaSnapshot", Map.of("version", 1, "inputs", snapshotInputs));
        }
        String fingerprint = UUID.randomUUID().toString();
        redis.putPreview(uid, fingerprint, requestDigest(uid, inputIds, workflow, normalizedOptions, normalizedPrompt));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workflowType", workflow.name());
        result.put("inputFiles", inputSummaries);
        result.put("operationSummary", operationSummary(workflow, normalizedOptions));
        if (collectQuality) result.put("confirmedOptions", new LinkedHashMap<>(normalizedOptions));
        result.put("dataQuality", qualitySummaries);
        result.put("previewFingerprint", fingerprint);
        return result;
    }

    private List<ExcelWorkbook.SheetSchema> selectedSchemas(List<ExcelWorkbook.SheetSchema> schemas,
                                                              WorkflowType workflow, Map<String, Object> options,
                                                              int inputIndex) {
        String selectedName = switch (workflow) {
            case SUMMARY -> Objects.toString(options.get("sheetName"), "");
            case RECONCILE -> Objects.toString(options.get(inputIndex == 0 ? "leftSheetName" : "rightSheetName"), "");
            case MERGE_CLEAN -> {
                Object sheetNames = options.get("sheetNames");
                if (sheetNames instanceof List<?> names && inputIndex < names.size()) yield Objects.toString(names.get(inputIndex), "");
                yield "";
            }
            case FREEFORM -> "";
        };
        if (selectedName.isBlank()) return schemas;
        return schemas.stream().filter(sheet -> sheet.name().equals(selectedName)).toList();
    }

    private List<ExcelWorkbook.Sheet> selectedQualitySheets(ExcelWorkbook workbook, WorkflowType workflow,
                                                            Map<String, Object> options, int fileIndex) {
        String selectedName = switch (workflow) {
            case SUMMARY -> Objects.toString(options.get("sheetName"), "");
            case RECONCILE -> Objects.toString(options.get(fileIndex == 0 ? "leftSheetName" : "rightSheetName"), "");
            case MERGE_CLEAN -> {
                Object sheetNames = options.get("sheetNames");
                if (sheetNames instanceof List<?> names && fileIndex < names.size()) yield Objects.toString(names.get(fileIndex), "");
                yield "";
            }
            case FREEFORM -> "";
        };
        if (!selectedName.isBlank()) return workbook.sheets().stream().filter(sheet -> sheet.name().equals(selectedName)).toList();
        return workbook.sheets();
    }

    @Transactional
    public Long create(Long uid, List<Long> fileIds, String workflowName, Map<String, Object> options,
                       String prompt, String previewFingerprint) {
        return createInternal(uid, fileIds, workflowName, options, prompt, previewFingerprint, false);
    }

    @Transactional
    public Long create(Long uid, Long fileId, String prompt) {
        return createInternal(uid, List.of(fileId), WorkflowType.FREEFORM.name(), Map.of(), prompt, null, true);
    }

    private Long createInternal(Long uid, List<Long> fileIds, String workflowName, Map<String, Object> options,
                                String prompt, String previewFingerprint, boolean legacy) {
        WorkflowType workflow = parseWorkflow(workflowName);
        List<Long> inputIds = normalizeIds(fileIds);
        validateCardinality(inputIds, workflow);
        Map<String, Object> normalizedOptions = normalizeOptions(workflow, options);
        PlanRepository.Plan plan = planFor(uid);
        long totalBytes = 0;
        long aggregateLimit = Math.multiplyExact(plan.maxFileSize(), AGGREGATE_SIZE_MULTIPLIER);
        for (Long fileId : inputIds) {
            FileRepository.FileRecord file = ownedFile(fileId, uid);
            validateSize(file, plan);
            totalBytes = Math.addExact(totalBytes, file.sizeBytes());
            if (totalBytes > aggregateLimit) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "combined files exceed plan limit");
        }

        String normalizedPrompt = prompt == null ? "" : prompt.trim();
        if (!legacy) {
            String expected = requestDigest(uid, inputIds, workflow, normalizedOptions, normalizedPrompt);
            String preview = previewFingerprint == null ? null : redis.getPreview(uid, previewFingerprint);
            if (preview == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), preview.getBytes(StandardCharsets.UTF_8)))
                throw new ApiException(HttpStatus.BAD_REQUEST, "preview expired or does not match task request");
        }

        String optionsJson;
        try {
            optionsJson = mapper.writeValueAsString(normalizedOptions);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid workflow options");
        }
        Long id = tasks.create(uid, inputIds, workflow.name(), optionsJson, normalizedPrompt);
        usage.consume(uid, id);
        if (!legacy) redis.deletePreview(uid, previewFingerprint);
        return id;
    }

    public TaskRepository.Task get(Long id, Long uid) {
        var task = tasks.owned(id, uid);
        if (task == null) throw new ApiException(HttpStatus.NOT_FOUND, "task not found");
        return task;
    }

    public TaskRepository.HistoryPage history(Long uid, int page, int size, String status, String workflowName,
                                              LocalDate fromDate, LocalDate toDate) {
        if (page < 0 || page > MAX_HISTORY_PAGE || size < 1 || size > MAX_HISTORY_PAGE_SIZE)
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid task history page or size");
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate))
            throw new ApiException(HttpStatus.BAD_REQUEST, "task history start date must not follow end date");
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (normalizedStatus != null && !HISTORY_STATUSES.contains(normalizedStatus))
            throw new ApiException(HttpStatus.BAD_REQUEST, "unsupported task status filter");
        String normalizedWorkflow = null;
        if (workflowName != null && !workflowName.isBlank()) {
            try {
                normalizedWorkflow = WorkflowType.parse(workflowName).name();
            } catch (IllegalArgumentException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "unsupported workflow filter");
            }
        }
        return tasks.history(uid, page, size, normalizedStatus, normalizedWorkflow, fromDate, toDate);
    }

    private PlanRepository.Plan planFor(Long uid) {
        var user = users.findById(uid);
        if (user == null) throw new ApiException(HttpStatus.NOT_FOUND, "user not found");
        var plan = plans.findByCode(user.planCode());
        if (plan == null) throw new ApiException(HttpStatus.FORBIDDEN, "user plan unavailable");
        return plan;
    }

    private FileRepository.FileRecord ownedFile(Long fileId, Long uid) {
        var file = files.findOwned(fileId, uid);
        if (file == null) throw new ApiException(HttpStatus.NOT_FOUND, "file not found");
        return file;
    }

    private void validateSize(FileRepository.FileRecord file, PlanRepository.Plan plan) {
        if (file.sizeBytes() > plan.maxFileSize()) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "file exceeds plan limit");
    }

    private List<Long> normalizeIds(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "at least one input file is required");
        if (fileIds.size() > MAX_INPUT_FILES) throw new ApiException(HttpStatus.BAD_REQUEST, "too many input files");
        if (fileIds.stream().anyMatch(Objects::isNull)) throw new ApiException(HttpStatus.BAD_REQUEST, "input file ID is required");
        if (new HashSet<>(fileIds).size() != fileIds.size()) throw new ApiException(HttpStatus.BAD_REQUEST, "duplicate input file IDs are not allowed");
        return List.copyOf(fileIds);
    }

    private void validateCardinality(List<Long> ids, WorkflowType workflow) {
        boolean valid = switch (workflow) {
            case FREEFORM, SUMMARY -> ids.size() == 1;
            case RECONCILE -> ids.size() == 2;
            case MERGE_CLEAN -> ids.size() >= 2;
        };
        if (!valid) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid input file count for workflow " + workflow);
    }

    private WorkflowType parseWorkflow(String workflowName) {
        try {
            return WorkflowType.parse(workflowName);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "unsupported workflow type");
        }
    }

    private Map<String, Object> normalizeOptions(WorkflowType workflow, Map<String, Object> options) {
        Map<String, Object> normalized = options == null ? new TreeMap<>() : new TreeMap<>(options);
        Object templateId = normalized.get("workflowTemplateId");
        if (templateId != null && (!(templateId instanceof String id) || !workflow.equals(TEMPLATE_WORKFLOWS.get(id))))
            throw new ApiException(HttpStatus.BAD_REQUEST, "workflow template does not match workflow type");
        Object requestedFormat = normalized.get("outputFormat");
        String format = requestedFormat == null ? "XLSX" : String.valueOf(requestedFormat).trim().toUpperCase(Locale.ROOT);
        if (!format.equals("XLSX") && !format.equals("CSV"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "unsupported output format");
        if (format.equals("CSV") && workflow != WorkflowType.SUMMARY && workflow != WorkflowType.MERGE_CLEAN)
            throw new ApiException(HttpStatus.BAD_REQUEST, "CSV output is supported only for summary and merge/cleanup workflows");
        normalized.put("outputFormat", format);
        return normalized;
    }

    private String requestDigest(Long uid, List<Long> ids, WorkflowType workflow,
                                 Map<String, Object> options, String prompt) {
        try {
            String canonical = uid + "|" + ids + "|" + workflow.name() + "|" + canonicalValue(options) + "|" + prompt;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String canonicalValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.keySet().stream().map(String::valueOf).sorted()
                    .map(key -> key + ":" + canonicalValue(map.get(key)))
                    .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        }
        if (value instanceof Collection<?> collection)
            return collection.stream().map(this::canonicalValue).collect(java.util.stream.Collectors.joining(",", "[", "]"));
        return Objects.toString(value, "null");
    }

    private Map<String, Object> operationSummary(WorkflowType workflow, Map<String, Object> options) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("workflow", workflow.name());
        Map<String, Object> rules = new TreeMap<>(options);
        rules.remove("schemaSnapshot");
        rules.remove("workflowTemplateId");
        summary.put("rules", rules);
        return summary;
    }
}
