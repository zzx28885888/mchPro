package com.excelai.task;

import java.util.UUID;

/** 中文/English：一次 Agent 执行的可持久化用量记录 / Persistable usage measurement for one Agent execution. */
public record LlmUsageRecord(
        UUID executionId,
        Long taskId,
        String providerCode,
        String modelName,
        Long inputTokens,
        Long outputTokens,
        Long cachedInputTokens,
        Long modelCallCount,
        Long llmDurationMs,
        String measurementStatus) {
}
