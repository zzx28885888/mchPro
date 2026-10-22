package com.excelai.task;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
/** 中文/English：写入独立的 LLM 用量表 / Writes to the separate LLM usage table. */
public class LlmUsageRepository {
    private final JdbcTemplate jdbc;

    public LlmUsageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int save(LlmUsageRecord record) {
        return jdbc.update("""
                        INSERT INTO llm_usage_record
                            (execution_id, task_id, provider_code, model_name, input_tokens, output_tokens,
                             cached_input_tokens, model_call_count, llm_duration_ms, measurement_status)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                record.executionId(), record.taskId(), record.providerCode(), record.modelName(),
                record.inputTokens(), record.outputTokens(), record.cachedInputTokens(),
                record.modelCallCount(), record.llmDurationMs(), record.measurementStatus());
    }
}
