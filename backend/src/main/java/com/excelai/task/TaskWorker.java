package com.excelai.task;

import com.excelai.agent.AgentService;
import com.excelai.file.FileRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
/**
 * 中文：后台编排任务状态、可信多文件上下文、Agent 执行与结果记录。
 * English: Orchestrates task status, trusted multi-file context, Agent execution, and result persistence.
 */
public class TaskWorker {
    private final TaskRepository tasks;
    private final FileRepository files;
    private final AgentService agent;
    private final TaskRedisService redis;
    private final TaskDataService taskData;

    public TaskWorker(TaskRepository t, FileRepository f, AgentService a, TaskRedisService r, TaskDataService d) {
        tasks = t;
        files = f;
        agent = a;
        redis = r;
        taskData = d;
    }

    public void submit(Long id, Long uid, Long fileId, String prompt, int maxCalls) {
        submit(id, uid, List.of(fileId), WorkflowType.FREEFORM.name(), Map.of(), prompt, maxCalls);
    }

    @Async("taskExecutor")
    public void submit(Long id, Long uid, List<Long> fileIds, String workflowType,
                       Map<String, Object> options, String prompt, int maxCalls) {
        // 中文：@Async 使用有界任务线程池；方法返回不代表 Agent 任务已经完成。
        // English: @Async uses the bounded task pool; returning does not mean the Agent task is complete.
        tasks.running(id);
        redis.update(id, "RUNNING", 10, null, null);
        try {
            for (Long fileId : fileIds) {
                if (files.findOwned(fileId, uid) == null) throw new IllegalArgumentException("input file not found");
            }
            agent.execute(uid, id, fileIds, workflowType, options, prompt, maxCalls);
            Long result = taskData.result(id);
            if (result == null) throw new IllegalStateException("Agent completed without exporting a result workbook");
            tasks.done(id, result);
            redis.update(id, "COMPLETED", 100, null, result);
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            tasks.fail(id, message);
            redis.update(id, "FAILED", 100, message, null);
        } finally {
            taskData.clear(id);
        }
    }
}