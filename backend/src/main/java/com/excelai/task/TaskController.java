package com.excelai.task;

import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
/**
 * 中文：向已登录用户开放任务创建和状态查询；每次查询都由当前身份限定任务所有权。
 * English: Exposes task creation/status APIs to authenticated users and scopes every lookup to the current owner.
 */
public class TaskController {
    private final TaskService s;

    public TaskController(TaskService s) {
        this.s = s;
    }

    /**
     * 中文：前端创建任务必须提供已上传文件 ID 和非空自然语言要求。
     * English: Task creation requires an uploaded file ID and a non-empty natural-language instruction.
     */
    public record Req(@NotNull Long fileId, @NotBlank String prompt) {
    }

    @PostMapping
    Map<String, Object> create(@RequestBody Req r, Authentication a) {
        // 中文：身份只从认证上下文读取，任务输入通过 DTO 校验。
        // English: The owner comes only from the authenticated context; the request DTO validates task inputs.
        Long id = s.create((Long) a.getPrincipal(), r.fileId(), r.prompt());
        return Map.of("taskId", id, "status", "QUEUED");
    }

    @GetMapping("/{id}")
    Map<String, Object> get(@PathVariable Long id, Authentication a) {
        var t = s.get(id, (Long) a.getPrincipal());
        return Map.of("taskId", t.id(), "status", t.status(), "progress", t.progress(), "resultFileId", t.resultFileId() == null ? 0 : t.resultFileId(), "error", t.errorMessage() == null ? "" : t.errorMessage());
    }
}
