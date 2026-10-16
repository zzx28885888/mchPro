package com.excelai.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
/** 中文：从 PostgreSQL 恢复并派发持久化任务。English: Recovers and dispatches durable tasks from PostgreSQL. */
public class TaskDispatcher {
    private static final Logger log = LoggerFactory.getLogger(TaskDispatcher.class);
    private static final int DISPATCH_BATCH_SIZE = 25;

    private final TaskRepository tasks;
    private final TaskWorker worker;
    private final ThreadPoolTaskExecutor executor;
    private volatile boolean ready;

    public TaskDispatcher(TaskRepository tasks, TaskWorker worker,
                          @Qualifier("taskExecutor") ThreadPoolTaskExecutor executor) {
        this.tasks = tasks;
        this.worker = worker;
        this.executor = executor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAfterStartup() {
        tasks.recoverRunning();
        ready = true;
    }

    @Scheduled(fixedDelayString = "${app.task.dispatch-delay-ms:1000}")
    public void dispatchQueuedTasks() {
        if (!ready) return;
        try {
            for (Long taskId : tasks.queuedIds(DISPATCH_BATCH_SIZE)) {
                if (!tasks.claimQueued(taskId)) continue;
                try {
                    executor.execute(() -> worker.run(taskId));
                } catch (TaskRejectedException e) {
                    tasks.requeue(taskId);
                    log.info("Task {} remains queued because the worker pool is full", taskId);
                    break;
                }
            }
        } catch (RuntimeException e) {
            log.error("Failed to dispatch queued tasks; the next scheduled poll will retry", e);
        }
    }
}
