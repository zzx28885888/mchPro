package com.excelai.task;

import com.excelai.agent.AgentService;
import com.excelai.file.FileRepository;
import com.excelai.plan.PlanRepository;
import com.excelai.user.UserRepository;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Service
/**
 * 中文：后台编排任务状态、可信多文件上下文、Agent 执行与结果记录。
 * English: Orchestrates task status, trusted multi-file context, Agent execution, and result persistence.
 */
public class TaskWorker {
    private final TaskRepository tasks;
    private final FileRepository files;
    private final UserRepository users;
    private final PlanRepository plans;
    private final AgentService agent;
    private final TaskDataService taskData;
    private final ObjectMapper mapper;

    public TaskWorker(TaskRepository t, FileRepository f, UserRepository u, PlanRepository p,
                      AgentService a, TaskDataService d, ObjectMapper m) {
        tasks = t;
        files = f;
        users = u;
        plans = p;
        agent = a;
        taskData = d;
        mapper = m;
    }

    public void run(Long id) {
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
            agent.execute(uid, id, fileIds, task.workflowType(), options, task.prompt(), plan.maxToolCalls());
            Long result = taskData.result(id);
            if (result == null) throw new IllegalStateException("Agent completed without exporting a result workbook");
            tasks.done(id, result);
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            tasks.fail(id, message);
        } finally {
            taskData.clear(id);
        }
    }
}
