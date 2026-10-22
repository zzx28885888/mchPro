package com.excelai.task;

import com.excelai.agent.AgentResult;
import com.excelai.agent.AgentService;
import com.excelai.agent.AgentUsageSummary;
import com.excelai.file.FileRepository;
import com.excelai.plan.PlanRepository;
import com.excelai.user.UserRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 中文/English：验证计量记录不会改变任务处理语义 / Verifies metering records do not change task processing semantics. */
class TaskWorkerUsageTest {
    @Test
    void persistsAgentUsageBeforeCheckingTheResultFile() {
        Fixture fixture = new Fixture();
        AgentUsageSummary usage = new AgentUsageSummary("deepseek", "deepseek-flash", 120L, 30L, 5L, 2L, 875L, "COMPLETE");
        when(fixture.agent.execute(any(UUID.class), eq(7L), eq(42L), eq(List.of(81L)), eq("FREEFORM"),
                anyMap(), eq("synthetic task"), eq(8))).thenReturn(new AgentResult("done", usage));
        when(fixture.taskData.result(42L)).thenReturn(99L);

        fixture.worker.run(42L);

        var record = org.mockito.ArgumentCaptor.forClass(LlmUsageRecord.class);
        var order = inOrder(fixture.agent, fixture.usage, fixture.taskData, fixture.tasks);
        order.verify(fixture.agent).execute(any(UUID.class), eq(7L), eq(42L), eq(List.of(81L)), eq("FREEFORM"),
                anyMap(), eq("synthetic task"), eq(8));
        order.verify(fixture.usage).save(record.capture());
        order.verify(fixture.taskData).result(42L);
        order.verify(fixture.tasks).done(42L, 99L);
        assertEquals(42L, record.getValue().taskId());
        assertEquals("deepseek", record.getValue().providerCode());
        assertEquals(120L, record.getValue().inputTokens());
        assertEquals("COMPLETE", record.getValue().measurementStatus());
    }

    @Test
    void persistsKnownUsageEvenWhenResultFileValidationFails() {
        Fixture fixture = new Fixture();
        when(fixture.agent.execute(any(UUID.class), anyLong(), anyLong(), anyList(), anyString(), anyMap(), anyString(), anyInt()))
                .thenReturn(new AgentResult("done", new AgentUsageSummary("ollama", "qwen3.5:9b", 20L, 5L, 0L, 1L, 400L, "COMPLETE")));
        when(fixture.taskData.result(42L)).thenReturn(null);

        fixture.worker.run(42L);

        verify(fixture.usage).save(argThat(record -> record.measurementStatus().equals("COMPLETE")));
        verify(fixture.tasks).fail(eq(42L), contains("without exporting"));
        verify(fixture.tasks, never()).done(anyLong(), anyLong());
    }

    @Test
    void storesUnavailableUsageWhenAgentCallFails() {
        Fixture fixture = new Fixture();
        when(fixture.agent.execute(any(UUID.class), anyLong(), anyLong(), anyList(), anyString(), anyMap(), anyString(), anyInt()))
                .thenThrow(new IllegalStateException("synthetic upstream failure"));

        fixture.worker.run(42L);

        var record = org.mockito.ArgumentCaptor.forClass(LlmUsageRecord.class);
        verify(fixture.usage).save(record.capture());
        assertNotNull(record.getValue().executionId());
        assertEquals("UNAVAILABLE", record.getValue().measurementStatus());
        assertNull(record.getValue().inputTokens());
        assertNull(record.getValue().outputTokens());
        verify(fixture.tasks).fail(42L, "synthetic upstream failure");
    }

    @Test
    void meteringStorageFailureDoesNotTurnSuccessfulTaskIntoFailure() {
        Fixture fixture = new Fixture();
        when(fixture.agent.execute(any(UUID.class), anyLong(), anyLong(), anyList(), anyString(), anyMap(), anyString(), anyInt()))
                .thenReturn(new AgentResult("done", new AgentUsageSummary("ollama", "qwen3.5:9b", 20L, 5L, 0L, 1L, 400L, "COMPLETE")));
        when(fixture.taskData.result(42L)).thenReturn(99L);
        doThrow(new IllegalStateException("synthetic storage failure")).when(fixture.usage).save(any(LlmUsageRecord.class));

        fixture.worker.run(42L);

        verify(fixture.tasks).done(42L, 99L);
        verify(fixture.tasks, never()).fail(anyLong(), anyString());
    }

    private static final class Fixture {
        private final TaskRepository tasks = mock(TaskRepository.class);
        private final FileRepository files = mock(FileRepository.class);
        private final UserRepository users = mock(UserRepository.class);
        private final PlanRepository plans = mock(PlanRepository.class);
        private final AgentService agent = mock(AgentService.class);
        private final TaskDataService taskData = mock(TaskDataService.class);
        private final LlmUsageRepository usage = mock(LlmUsageRepository.class);
        private final TaskWorker worker;

        private Fixture() {
            when(tasks.findById(42L)).thenReturn(new TaskRepository.Task(42L, 7L, 81L, "synthetic task",
                    "FREEFORM", "{}", "RUNNING", 10, null, null, null));
            when(tasks.inputFileIds(42L)).thenReturn(List.of(81L));
            when(users.findById(7L)).thenReturn(new UserRepository.User(7L, "synthetic@example.test", "hash", "BASIC"));
            when(plans.findByCode("BASIC")).thenReturn(new PlanRepository.Plan("BASIC", "Basic", 10, 1000, 8, 1000));
            when(files.findOwned(81L, 7L)).thenReturn(new FileRepository.FileRecord(81L, 7L, "fake.xlsx", "stored.xlsx",
                    "private-path", 12, "application/octet-stream", "INPUT"));
            worker = new TaskWorker(tasks, files, users, plans, agent, taskData, new ObjectMapper(), usage);
        }
    }
}
