package com.example.agent.tool.model;

/**
 * 中文：统一工具调用响应，包含执行结果或错误码、耗时和审计 ID。
 * English: Uniform tool response with result or error code, execution time, and audit ID.
 */
public record ToolExecuteResponse(
        boolean success,
        Object data,
        String error,
        long durationMs,
        String auditId
) {
}
