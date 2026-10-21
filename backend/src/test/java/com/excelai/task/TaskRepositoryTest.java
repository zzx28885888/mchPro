package com.excelai.task;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.excelai.persistence.entity.AiTaskEntity;
import com.excelai.persistence.mapper.AiTaskMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TaskRepositoryTest {
    @Test
    void summaryCompareAndSetUpdatesOnlyWhenStoredSummaryMatches() {
        AiTaskMapper mapper = mock(AiTaskMapper.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update("UPDATE ai_task SET result_summary=? WHERE id=? AND status='COMPLETED' AND BINARY result_summary=BINARY ?",
                "new", 12L, "old")).thenReturn(1);
        when(jdbc.update("UPDATE ai_task SET result_summary=? WHERE id=? AND status='COMPLETED' AND BINARY result_summary=BINARY ?",
                "newer", 12L, "old")).thenReturn(0);
        TaskRepository repository = new TaskRepository(mapper, jdbc);

        assertTrue(repository.updateSummaryIfUnchanged(12L, "old", "new"));
        assertFalse(repository.updateSummaryIfUnchanged(12L, "old", "newer"));
    }

    @Test
    void summaryCompareAndSetHandlesNullStoredSummary() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update("UPDATE ai_task SET result_summary=? WHERE id=? AND status='COMPLETED' AND result_summary IS NULL",
                "initialized", 12L)).thenReturn(1);
        TaskRepository repository = new TaskRepository(mock(AiTaskMapper.class), jdbc);

        assertTrue(repository.updateSummaryIfUnchanged(12L, null, "initialized"));
    }

    @Test
    void taskHistoryScopesCountAndRowsToOwnerAndAppliesFilters() {
        AiTaskMapper mapper = mock(AiTaskMapper.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(mapper.selectCount(any())).thenReturn(1L);
        when(mapper.selectList(any())).thenReturn(List.of());
        TaskRepository repository = new TaskRepository(mapper, jdbc);

        var result = repository.history(17L, 2, 10, "COMPLETED", "SUMMARY",
                LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-19"));

        assertEquals(1L, result.total());
        assertEquals(2, result.page());
        assertEquals(10, result.size());
        var queries = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectCount(queries.capture());
        assertTrue(queries.getValue().getSqlSegment().contains("user_id"));
        assertTrue(queries.getValue().getSqlSegment().contains("status"));
        assertTrue(queries.getValue().getSqlSegment().contains("workflow_type"));
        assertTrue(queries.getValue().getSqlSegment().contains("created_at"));
        verify(mapper).selectList(queries.capture());
        assertTrue(queries.getValue().getSqlSegment().contains("user_id"));
    }
}
