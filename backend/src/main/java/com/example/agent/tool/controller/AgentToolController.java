package com.example.agent.tool.controller;

import com.example.agent.tool.model.*;
import com.example.agent.tool.registry.ToolRegistry;
import com.example.agent.tool.service.ToolExecutionService;
import com.excelai.plan.PlanRepository;
import com.excelai.task.TaskRepository;
import com.excelai.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;

import java.security.MessageDigest;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/internal/agent/tools")
/**
 * 中文：仅供 Python Agent 服务调用的内部工具 API；共享令牌与用户/任务/套餐检查共同保护执行边界。
 * English: Internal API for the Python Agent; a shared token and user/task/plan checks protect tool execution.
 */
public class AgentToolController {

    private final ToolRegistry registry;
    private final ToolExecutionService executionService;
    private final TaskRepository tasks;
    private final UserRepository users;
    private final PlanRepository plans;
    @Value("${agent.internal-token}")
    private String internalToken;

    public AgentToolController(ToolRegistry registry, ToolExecutionService executionService, TaskRepository tasks, UserRepository users, PlanRepository plans) {
        this.registry = registry;
        this.executionService = executionService;
        this.tasks = tasks;
        this.users = users;
        this.plans = plans;
    }

    @GetMapping
    public Collection<ToolDefinition> listTools(@RequestHeader(value = "X-Agent-Token", required = false) String token) {
        requireToken(token);
        return registry.all().stream()
                .map(t -> t.definition())
                .collect(Collectors.toList());
    }

    @PostMapping("/{toolName}/execute")
    public ToolExecuteResponse execute(
            @PathVariable String toolName,
            @RequestHeader(value = "X-Agent-Token", required = false) String token,
            @Valid @RequestBody ToolExecuteRequest request) {
        requireToken(token);

        // 中文：Excel 工具必须绑定真实任务，并再次校验文件归属及套餐许可；不能信任模型提出的用户 ID。
        // English: Excel tools must be tied to a real task, owned input file, and plan grant; model-provided user IDs are never trusted.
        Map<String, Object> context = request.context() == null ? Map.of() : request.context();
        Set<String> permissions;
        if (Set.of("read_excel", "filter", "sort", "top", "export_excel").contains(toolName)) {
            Long userId = number(context.get("userId"));
            Long taskId = number(context.get("taskId"));
            Long inputFileId = number(context.get("inputFileId"));
            var task = userId == null || taskId == null ? null : tasks.owned(taskId, userId);
            List<Long> taskInputIds = task == null ? List.of() : tasks.inputFileIds(taskId);
            List<Long> suppliedInputIds = longList(context.get("inputFileIds"));
            if (suppliedInputIds.isEmpty() && inputFileId != null) suppliedInputIds = List.of(inputFileId);
            if (task == null || !Objects.equals(task.inputFileId(), inputFileId) || !taskInputIds.equals(suppliedInputIds))
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
            String plan = users.findById(userId).planCode();
            if (!plans.toolEnabled(plan, toolName))
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
            permissions = Set.of("excel:" + toolName);
        } else {
            permissions = request.principal().equals("langgraph") ? Set.of("user:read", "order:read", "debug:read") : Set.of();
        }
        return executionService.execute(
                toolName,
                request.arguments(),
                request.principal(),
                permissions,
                context);
    }

    private void requireToken(String token) {
        if (token == null || !MessageDigest.isEqual(internalToken.getBytes(java.nio.charset.StandardCharsets.UTF_8), token.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED);
    }

    private List<Long> longList(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        List<Long> result = new ArrayList<>();
        for (Object item : values) {
            Long parsed = number(item);
            if (parsed == null) return List.of();
            result.add(parsed);
        }
        return List.copyOf(result);
    }
    private Long number(Object o) {
        return o instanceof Number n ? n.longValue() : null;
    }
}
