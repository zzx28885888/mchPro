package com.excelai.agent;

/** 中文/English：Python Agent 返回的单次执行用量摘要 / Usage summary returned by the Python Agent for one execution. */
public record AgentUsageSummary(
        String providerCode,
        String modelName,
        Long inputTokens,
        Long outputTokens,
        Long cachedInputTokens,
        Long modelCallCount,
        Long llmDurationMs,
        String measurementStatus) {
}
