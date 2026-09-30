package com.excelai.task;

import com.excelai.common.ApiException;
import com.excelai.file.FileRepository;
import com.excelai.plan.PlanRepository;
import com.excelai.usage.UsageService;
import com.excelai.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
/**
 * 中文：同步完成任务创建前检查（文件归属、套餐文件大小、月额度），再登记任务并异步派发。
 * English: Performs pre-dispatch checks (file ownership, plan file size, monthly quota), persists the task, and submits it asynchronously.
 */
public class TaskService {
    private final TaskRepository tasks;
    private final FileRepository files;
    private final UserRepository users;
    private final PlanRepository plans;
    private final UsageService usage;
    private final TaskWorker worker;
    private final TaskRedisService redis;

    public TaskService(TaskRepository t, FileRepository f, UserRepository u, PlanRepository p, UsageService x, TaskWorker w, TaskRedisService r) {
        tasks = t;
        files = f;
        users = u;
        plans = p;
        usage = x;
        worker = w;
        redis = r;
    }

    public Long create(Long uid, Long fileId, String prompt) {
        // 中文：先验证用户确实拥有输入文件，避免跨用户引用文件 ID。
        // English: Verify ownership before task creation to prevent cross-user file-ID references.
        var f = files.findOwned(fileId, uid);
        if (f == null) throw new ApiException(HttpStatus.NOT_FOUND, "file not found");
        var plan = plans.findByCode(users.findById(uid).planCode());
        if (f.sizeBytes() > plan.maxFileSize())
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "file exceeds plan limit");
        Long id = tasks.create(uid, fileId, prompt);
        try {
            usage.consume(uid, id);
        } catch (RuntimeException e) {
            tasks.fail(id, e.getMessage());
            throw e;
        }
        redis.update(id, "QUEUED", 0, null, null);
        // 中文：API 立即返回任务 ID；耗时的模型和 Excel 工作在独立线程池运行。
        // English: Return the task ID immediately; model and spreadsheet work runs on the configured task pool.
        worker.submit(id, uid, fileId, prompt, plan.maxToolCalls());
        return id;
    }

    public TaskRepository.Task get(Long id, Long uid) {
        var t = tasks.owned(id, uid);
        if (t == null) throw new ApiException(HttpStatus.NOT_FOUND, "task not found");
        return t;
    }
}
