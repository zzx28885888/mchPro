package com.excelai.task;

import com.excelai.agent.AgentService;
import com.excelai.agent.AgentResult;
import com.excelai.agent.AgentUsageSummary;
import com.excelai.file.FileRepository;
import com.excelai.plan.PlanRepository;
import com.excelai.user.UserRepository;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

@Service
/**
 * 中文：后台编排任务状态、可信多文件上下文、Agent 执行与结果记录。
 * English: Orchestrates task status, trusted multi-file context, Agent execution, and result persistence.
 */
public class TaskWorker {
    private static final Logger log = LoggerFactory.getLogger(TaskWorker.class);
    private final TaskRepository tasks;
    private final FileRepository files;
    private final UserRepository users;
    private final PlanRepository plans;
    private final AgentService agent;
    private final TaskDataService taskData;
    private final ObjectMapper mapper;
    private final LlmUsageRepository usageRecords;

    public TaskWorker(TaskRepository t, FileRepository f, UserRepository u, PlanRepository p,
                      AgentService a, TaskDataService d, ObjectMapper m, LlmUsageRepository usageRecords) {
        tasks = t;
        files = f;
        users = u;
        plans = p;
        agent = a;
        taskData = d;
        mapper = m;
        this.usageRecords = usageRecords;
    }

    public void run(Long id) {
        UUID executionId = null;
        boolean agentReturned = false;
        try {
            TaskRepository.Task task = tasks.findById(id);
            if (task == null) throw new IllegalStateException("Task not found");
            Long uid = task.userId();
            var user = users.findById(uid);
            var plan = user == null ? null : plans.findByCode(user.planCode());
            if (plan == null) throw new IllegalStateException("User plan unavailable");
            var fileIds = tasks.inputFileIds(id);
            Map<String, Object> options = mapper.readValue(task.workflowOptions(), new TypeReference<>() { });
            for (Long fileId : fileIds) {
                if (files.findOwned(fileId, uid) == null) throw new IllegalArgumentException("input file not found");
            }
            executionId = UUID.randomUUID();
            AgentResult agentResult = agent.execute(executionId, uid, id, fileIds, task.workflowType(),
                    executionOptions(options), task.prompt(), plan.maxToolCalls());
            agentReturned = true;
            persistUsageSafely(id, executionId, agentResult == null ? null : agentResult.usage());
            Long result = taskData.result(id);
            if (result == null) throw new IllegalStateException("Agent completed without exporting a result file");
            tasks.done(id, result);
        } catch (Exception e) {
            if (executionId != null && !agentReturned) {
                persistUsageSafely(id, executionId, null);
            }
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            tasks.fail(id, message);
        } finally {
            taskData.clear(id);
        }
    }

    private void persistUsageSafely(Long taskId, UUID executionId, AgentUsageSummary usage) {
        String providerCode = usage == null || usage.providerCode() == null ? "unknown" : usage.providerCode();
        String modelName = usage == null || usage.modelName() == null ? "unknown" : usage.modelName();
        Long inputTokens = usage == null ? null : usage.inputTokens();
        Long outputTokens = usage == null ? null : usage.outputTokens();
        Long cachedInputTokens = usage == null ? null : usage.cachedInputTokens();
        Long modelCallCount = usage == null || usage.modelCallCount() == null ? 0L : usage.modelCallCount();
        Long llmDurationMs = usage == null || usage.llmDurationMs() == null ? 0L : usage.llmDurationMs();
        String measurementStatus = usage == null || usage.measurementStatus() == null
                ? "UNAVAILABLE" : usage.measurementStatus();
        try {
            usageRecords.save(new LlmUsageRecord(executionId, taskId, providerCode, modelName, inputTokens,
                    outputTokens, cachedInputTokens, modelCallCount, llmDurationMs, measurementStatus));
        } catch (Exception e) {
            log.warn("LLM usage persistence failed task_id={} execution_id={} error_type={}",
                    taskId, executionId, e.getClass().getSimpleName());
        }
    }

    static Map<String, Object> executionOptions(Map<String, Object> persistedOptions) {
        Map<String, Object> options = new java.util.TreeMap<>(persistedOptions);
        options.remove("workflowTemplateId");
        options.remove("schemaSnapshot");
        return options;
    }
}
