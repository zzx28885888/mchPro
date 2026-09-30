package com.example.agent.tool.model;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * 中文：内部工具执行请求。context 由可信后端传递，不能由模型参数覆盖。
 * English: Internal tool-execution request. Context is supplied by trusted services and must not come from model arguments.
 */
public record ToolExecuteRequest(
        Map<String, Object> arguments,
        @NotBlank String principal,
        Map<String, Object> context
) {
}
