package com.excelai.task;

import com.excelai.agent.AgentService;
import com.excelai.file.FileRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
/**
 * 中文：后台任务编排器：更新状态、调用 LangGraph、保存结果引用，并把异常转成 FAILED 状态。
 * English: Background task orchestrator: updates state, calls LangGraph, records the output reference, and converts failures to FAILED.
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

    @Async("taskExecutor")
    public void submit(Long id, Long uid, Long fileId, String prompt, int maxCalls) {
        // English: @Async selects the bounded task pool; returning from this method does not mean the Agent task is complete.
        // 中文：@Async 指定有界任务线程池；此方法返回不代表 Agent 任务已经完成。
        tasks.running(id);
        redis.update(id, "RUNNING", 10, null, null);
        try {
            var in = files.findOwned(fileId, uid);
            if (in == null) throw new IllegalArgumentException("input file not found");
            agent.execute(uid, id, fileId, prompt, maxCalls);
            // 中文：必须由 export_excel 工具生成结果；只返回自然语言回答不会标记为成功。
            // English: Completion requires export_excel to produce a file; a text-only answer is not treated as success.
            Long result = taskData.result(id);
            if (result == null) throw new IllegalStateException("Agent completed without exporting a result workbook");
            tasks.done(id, result);
            redis.update(id, "COMPLETED", 100, null, result);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            tasks.fail(id, msg);
            redis.update(id, "FAILED", 100, msg, null);
        } finally {
            taskData.clear(id);
        }
    }
}
