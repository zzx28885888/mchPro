package com.excelai.task;

import com.excelai.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

@RestController
@RequestMapping("/api/tasks")
/**
 * 中文：向登录用户开放任务预览、创建和状态查询，并按当前身份限制所有操作。
 * English: Exposes task preview, creation, and status to authenticated users, scoped to the current identity.
 */
public class TaskController {
    private final TaskService service;
    private final ObjectMapper mapper;

    public TaskController(TaskService service, ObjectMapper mapper) {
        this.service = service;
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

    private Map<String, Object> parseSummary(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return Map.of("message", "Result summary is unavailable");
        }
    }
}