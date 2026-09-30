package com.example.agent.tool.service;

import com.example.agent.audit.AuditService;
import com.example.agent.tool.model.ToolExecuteResponse;
import com.example.agent.tool.registry.RegisteredTool;
import com.example.agent.tool.registry.ToolRegistry;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;

@Service
/**
 * 中文：工具调用的执行边界，统一完成存在性、权限、参数检查、超时控制和审计。
 * English: The execution boundary for tools, applying existence, permission, argument, timeout, and audit checks consistently.
 */
public class ToolExecutionService {

    private final ToolRegistry registry;
    private final AuditService auditService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ToolExecutionService(ToolRegistry registry, AuditService auditService) {
        this.registry = registry;
        this.auditService = auditService;
    }

    public ToolExecuteResponse execute(
            String toolName,
            Map<String, Object> arguments,
            String principal,
            Set<String> permissions,
            Map<String, Object> context) {

        // 中文：先在提交线程完成静态校验，再把实际工具逻辑交给线程池，避免无效请求占用执行线程。
        // English: Perform static validation before submitting work so invalid calls do not consume worker threads.
        long start = System.currentTimeMillis();
        String auditId = UUID.randomUUID().toString();

        RegisteredTool tool = registry.get(toolName);
        if (tool == null) {
            return fail(auditId, start, "TOOL_NOT_FOUND");
        }

        if (!permissions.contains(tool.definition().permission())) {
            auditService.record(auditId, principal, toolName, "DENIED", start, null);
            return fail(auditId, start, "PERMISSION_DENIED");
        }

        String validationError = validate(tool, arguments);
        if (validationError != null) {
            auditService.record(auditId, principal, toolName, "INVALID_ARGUMENTS", start, validationError);
            return fail(auditId, start, validationError);
        }

        // 中文：上下文必须在工作线程内部设置，因为 ThreadLocal 不会自动跨线程传播。
        // English: Install context inside the worker because ThreadLocal values do not propagate automatically across threads.
        Callable<Object> task = () -> {
            AgentToolContext.set(context);
            try {
                return invoke(tool, arguments);
            } finally {
                AgentToolContext.clear();
            }
        };
        Future<Object> future = executor.submit(task);

        try {
            Object result = future.get(tool.definition().timeoutMs(), TimeUnit.MILLISECONDS);
            auditService.record(auditId, principal, toolName, "SUCCESS", start, null);
            return new ToolExecuteResponse(true, result, null,
                    System.currentTimeMillis() - start, auditId);
        } catch (TimeoutException e) {
            // 中文：超时后中断任务并记录审计；工具实现仍应响应线程中断。
            // English: Cancel and audit timed-out work; tool implementations should honor thread interruption.
            future.cancel(true);
            auditService.record(auditId, principal, toolName, "TIMEOUT", start,
                    "timeoutMs=" + tool.definition().timeoutMs());
            return fail(auditId, start, "TOOL_TIMEOUT");
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            auditService.record(auditId, principal, toolName, "ERROR", start,
                    cause.getClass().getSimpleName() + ": " + cause.getMessage());
            return fail(auditId, start, "TOOL_EXECUTION_FAILED");
        }
    }

    private Object invoke(RegisteredTool tool, Map<String, Object> args) throws Exception {
        List<Object> values = new ArrayList<>();
        for (RegisteredTool.ParameterSpec p : tool.parameters().values()) {
            Object value = args.get(p.name());
            values.add(convert(value, p.type()));
        }
        Method method = tool.method();
        method.setAccessible(true);
        return method.invoke(tool.bean(), values.toArray());
    }

    private Object convert(Object value, Class<?> type) {
        if (value == null) return null;
        if (type.isInstance(value)) return value;
        if ((type == int.class || type == Integer.class) && value instanceof Number n) return n.intValue();
        if ((type == long.class || type == Long.class) && value instanceof Number n) return n.longValue();
        if ((type == double.class || type == Double.class) && value instanceof Number n) return n.doubleValue();
        if ((type == boolean.class || type == Boolean.class) && value instanceof Boolean b) return b;
        if (type == String.class) return String.valueOf(value);
        throw new IllegalArgumentException("Unsupported argument conversion: " + value.getClass() + " -> " + type);
    }

    private String validate(RegisteredTool tool, Map<String, Object> args) {
        if (args == null) return "ARGUMENTS_REQUIRED";
        for (RegisteredTool.ParameterSpec p : tool.parameters().values()) {
            if (p.required() && !args.containsKey(p.name())) {
                return "MISSING_PARAMETER:" + p.name();
            }
            Object value = args.get(p.name());
            if (value == null && p.required()) return "NULL_PARAMETER:" + p.name();
            if (value != null && !compatible(value, p.type())) {
                return "INVALID_PARAMETER_TYPE:" + p.name();
            }
        }
        for (String key : args.keySet()) {
            if (!tool.parameters().containsKey(key)) return "UNKNOWN_PARAMETER:" + key;
        }
        return null;
    }

    private boolean compatible(Object value, Class<?> type) {
        if (type == String.class) return value instanceof String;
        if (type == int.class || type == Integer.class ||
                type == long.class || type == Long.class ||
                type == double.class || type == Double.class) return value instanceof Number;
        if (type == boolean.class || type == Boolean.class) return value instanceof Boolean;
        return type.isInstance(value);
    }

    private ToolExecuteResponse fail(String auditId, long start, String error) {
        return new ToolExecuteResponse(false, null, error,
                System.currentTimeMillis() - start, auditId);
    }
}
