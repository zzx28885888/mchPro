package com.excelai.task;

import com.excelai.common.ApiException;
import com.excelai.excel.ExcelService;
import com.excelai.file.FileRepository;
import com.excelai.file.FileStorageService;
import com.excelai.plan.PlanRepository;
import com.excelai.usage.UsageService;
import com.excelai.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
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

    private final TaskRepository tasks;
    private final FileRepository files;
    private final UserRepository users;
    private final PlanRepository plans;
    private final UsageService usage;
    private final TaskWorker worker;
    private final TaskRedisService redis;
    private final ExcelService excel;
    private final FileStorageService storage;
    private final ObjectMapper mapper;

    public TaskService(TaskRepository t, FileRepository f, UserRepository u, PlanRepository p,
                       UsageService x, TaskWorker w, TaskRedisService r, ExcelService e,
                       FileStorageService s, ObjectMapper m) {
        tasks = t;
        files = f;
        users = u;
        plans = p;
        usage = x;
        worker = w;
        redis = r;
        excel = e;
        storage = s;
        mapper = m;
    }

    public Map<String, Object> preview(Long uid, List<Long> fileIds, String workflowName,
                                       Map<String, Object> options, String prompt) {
        WorkflowType workflow = parseWorkflow(workflowName);
        List<Long> inputIds = normalizeIds(fileIds);
        validateCardinality(inputIds, workflow);
        PlanRepository.Plan plan = planFor(uid);
        List<Map<String, Object>> inputSummaries = new ArrayList<>();
        long totalBytes = 0;
        long aggregateLimit = Math.multiplyExact(plan.maxFileSize(), AGGREGATE_SIZE_MULTIPLIER);
        for (Long fileId : inputIds) {
            FileRepository.FileRecord file = ownedFile(fileId, uid);
            validateSize(file, plan);
            totalBytes = Math.addExact(totalBytes, file.sizeBytes());
            if (totalBytes > aggregateLimit) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "combined files exceed plan limit");
            var workbook = excel.inspectWorkbook(storage.path(file.storagePath()));
            List<Map<String, Object>> sheetSummaries = workbook.stream().map(sheet -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", sheet.name());
                item.put("headers", sheet.headers());
                return item;
            }).toList();
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("fileId", file.id());
            input.put("name", file.originalName());
            input.put("sizeBytes", file.sizeBytes());
            input.put("sheets", sheetSummaries);
            inputSummaries.add(input);
        }

        Map<String, Object> normalizedOptions = options == null ? Map.of() : new TreeMap<>(options);
        String normalizedPrompt = prompt == null ? "" : prompt.trim();
        String fingerprint = UUID.randomUUID().toString();
        redis.putPreview(uid, fingerprint, requestDigest(uid, inputIds, workflow, normalizedOptions, normalizedPrompt));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workflowType", workflow.name());
        result.put("inputFiles", inputSummaries);
        result.put("operationSummary", operationSummary(workflow, normalizedOptions));
        result.put("previewFingerprint", fingerprint);
        return result;
    }

    public Long create(Long uid, List<Long> fileIds, String workflowName, Map<String, Object> options,
                       String prompt, String previewFingerprint) {
        return createInternal(uid, fileIds, workflowName, options, prompt, previewFingerprint, false);
    }

    public Long create(Long uid, Long fileId, String prompt) {
        return createInternal(uid, List.of(fileId), WorkflowType.FREEFORM.name(), Map.of(), prompt, null, true);
    }

    private Long createInternal(Long uid, List<Long> fileIds, String workflowName, Map<String, Object> options,
                                String prompt, String previewFingerprint, boolean legacy) {
        WorkflowType workflow = parseWorkflow(workflowName);
        List<Long> inputIds = normalizeIds(fileIds);
        validateCardinality(inputIds, workflow);
        PlanRepository.Plan plan = planFor(uid);
        long totalBytes = 0;
        long aggregateLimit = Math.multiplyExact(plan.maxFileSize(), AGGREGATE_SIZE_MULTIPLIER);
        for (Long fileId : inputIds) {
            FileRepository.FileRecord file = ownedFile(fileId, uid);
            validateSize(file, plan);
            totalBytes = Math.addExact(totalBytes, file.sizeBytes());
            if (totalBytes > aggregateLimit) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "combined files exceed plan limit");
        }

        Map<String, Object> normalizedOptions = options == null ? Map.of() : new TreeMap<>(options);
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
        try {
            usage.consume(uid, id);
        } catch (RuntimeException e) {
            tasks.fail(id, e.getMessage());
            throw e;
        }
        redis.update(id, "QUEUED", 0, null, null);
        worker.submit(id, uid, inputIds, workflow.name(), normalizedOptions, normalizedPrompt, plan.maxToolCalls());
        if (!legacy) redis.deletePreview(uid, previewFingerprint);
        return id;
    }

    public TaskRepository.Task get(Long id, Long uid) {
        var task = tasks.owned(id, uid);
        if (task == null) throw new ApiException(HttpStatus.NOT_FOUND, "task not found");
        return task;
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
        summary.put("rules", options);
        return summary;
    }
}