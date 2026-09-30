package com.example.agent.tool.model;

import java.util.Map;

/**
 * 中文：发给 LangGraph/LLM 的工具元数据；inputSchema 描述可提交的 JSON 参数结构。
 * English: Tool metadata sent to LangGraph/LLM; inputSchema describes the accepted JSON argument shape.
 */
public record ToolDefinition(
        String name,
        String description,
        String permission,
        long timeoutMs,
        Map<String, Object> inputSchema
) {
}
