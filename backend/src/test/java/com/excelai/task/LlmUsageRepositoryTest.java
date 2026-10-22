package com.excelai.task;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 中文/English：验证执行用量记录保留 NULL 计数 / Verifies execution usage persistence preserves NULL counters. */
class LlmUsageRepositoryTest {
    @Test
    void savesNullableTokenCountersWithoutConvertingThemToZero() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        LlmUsageRepository repository = new LlmUsageRepository(jdbc);
        UUID executionId = UUID.fromString("b6db8fca-ae06-4d1b-a67b-17f4c6c6d810");
        LlmUsageRecord record = new LlmUsageRecord(executionId, 42L, "deepseek", "deepseek-flash",
                120L, 30L, null, 2L, 875L, "PARTIAL");

        repository.save(record);

        var args = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(contains("INSERT INTO llm_usage_record"), args.capture());
        assertEquals(executionId, args.getValue()[0]);
        assertEquals(42L, args.getValue()[1]);
        assertEquals(120L, args.getValue()[4]);
        assertEquals(30L, args.getValue()[5]);
        assertEquals(null, args.getValue()[6]);
        assertEquals("PARTIAL", args.getValue()[9]);
    }
}
