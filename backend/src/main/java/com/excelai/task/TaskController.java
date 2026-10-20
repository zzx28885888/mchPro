package com.excelai.task;

import com.excelai.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/tasks")
/**
 * 中文：向登录用户开放任务预览、创建和状态查询，并按当前身份限制所有操作。
 * English: Exposes task preview, creation, and status to authenticated users, scoped to the current identity.
 */
public class TaskController {
    private final TaskService service;
    private final TaskReviewService reviews;
    private final ObjectMapper mapper;

    public TaskController(TaskService service, TaskReviewService reviews, ObjectMapper mapper) {
        this.service = service;
        this.reviews = reviews;
        this.mapper = mapper;
    }

    public record PreviewReq(List<Long> fileIds, Long fileId, WorkflowType workflowType,
                             Map<String, Object> options, String prompt) {
        List<Long> resolvedFileIds() {
            if (fileIds != null && fileId != null) throw new ApiException(HttpStatus.BAD_REQUEST, "provide fileIds or fileId, not both");
            if (fileIds != null) return fileIds;
            return fileId == null ? List.of() : List.of(fileId);
        }
    }

    public record Req(List<Long> fileIds, Long fileId, WorkflowType workflowType, Map<String, Object> options,
                      String prompt, String previewFingerprint) {
        List<Long> resolvedFileIds() {
            if (fileIds != null && fileId != null) throw new ApiException(HttpStatus.BAD_REQUEST, "provide fileIds or fileId, not both");
            if (fileIds != null) return fileIds;
            return fileId == null ? List.of() : List.of(fileId);
        }

        boolean isLegacy() {
            return fileIds == null && fileId != null && workflowType == null && options == null && previewFingerprint == null;
        }
    }

    @PostMapping("/preview")
    Map<String, Object> preview(@RequestBody PreviewReq request, Authentication authentication) {
        return service.preview((Long) authentication.getPrincipal(), request.resolvedFileIds(),
                request.workflowType() == null ? null : request.workflowType().name(), request.options(), request.prompt());
    }

    @PostMapping
    Map<String, Object> create(@RequestBody Req request, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Long taskId = request.isLegacy()
                ? service.create(userId, request.fileId(), request.prompt())
                : service.create(userId, request.resolvedFileIds(), request.workflowType() == null ? null : request.workflowType().name(), request.options(),
                        request.prompt(), request.previewFingerprint());
        return Map.of("taskId", taskId, "status", "QUEUED");
    }

    @GetMapping
    Map<String, Object> history(@RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size,
                                @RequestParam(required = false) String status,
                                @RequestParam(required = false) String workflowType,
                                @RequestParam(required = false) LocalDate fromDate,
                                @RequestParam(required = false) LocalDate toDate,
                                Authentication authentication) {
        var history = service.history((Long) authentication.getPrincipal(), page, size, status, workflowType, fromDate, toDate);
        List<Map<String, Object>> items = history.items().stream().map(task -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskId", task.id());
            item.put("status", task.status());
            item.put("progress", task.progress());
            item.put("workflowType", task.workflowType());
            item.put("prompt", task.prompt());
            item.put("workflowOptions", parseObject(task.workflowOptions()));
            item.put("createdAt", task.createdAt());
            item.put("completedAt", task.completedAt());
            item.put("resultFileId", task.resultFileId() == null ? 0 : task.resultFileId());
            item.put("error", task.errorMessage() == null ? "" : task.errorMessage());
            item.put("resultSummary", parseSummary(task.resultSummary()));
            item.put("inputFiles", task.inputFiles().stream().map(file -> Map.of("fileId", file.fileId(), "name", file.name())).toList());
            return item;
        }).toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", items);
        response.put("page", history.page());
        response.put("size", history.size());
        response.put("total", history.total());
        return response;
    }

    @GetMapping("/{id}")
    Map<String, Object> get(@PathVariable Long id, Authentication authentication) {
        var task = service.get(id, (Long) authentication.getPrincipal());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskId", task.id());
        response.put("status", task.status());
        response.put("progress", task.progress());
        response.put("resultFileId", task.resultFileId() == null ? 0 : task.resultFileId());
        response.put("error", task.errorMessage() == null ? "" : task.errorMessage());
        response.put("workflowType", task.workflowType() == null ? "FREEFORM" : task.workflowType());
        response.put("resultSummary", parseSummary(task.resultSummary()));
        return response;
    }

    @GetMapping("/{id}/review")
    TaskReviewService.ReviewPage review(@PathVariable Long id,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "50") int size,
                                        Authentication authentication) {
        return reviews.getReview(id, (Long) authentication.getPrincipal(), page, size);
    }

    @PutMapping("/{id}/review")
    TaskReviewService.ReviewPage submitReview(@PathVariable Long id,
                                              @RequestBody TaskReviewService.ReviewRequest request,
                                              Authentication authentication) {
        return reviews.submitReview(id, (Long) authentication.getPrincipal(), request);
    }

    private Map<String, Object> parseSummary(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return Map.of("message", "Result summary is unavailable");
        }
    }

    private Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return Map.of();
        }
    }
}
