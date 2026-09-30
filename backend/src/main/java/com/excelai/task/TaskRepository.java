package com.excelai.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.excelai.persistence.entity.AiTaskEntity;
import com.excelai.persistence.mapper.AiTaskMapper;
import org.springframework.stereotype.Repository;

/**
 * 中文：ai_task 数据访问层。通过 MyBatis-Plus 完成任务记录的增改查。
 * English: Persistence adapter for ai_task, using MyBatis-Plus for task record CRUD.
 */
@Repository
public class TaskRepository {
    private final AiTaskMapper mapper;

    public TaskRepository(AiTaskMapper mapper) {
        this.mapper = mapper;
    }

    public record Task(Long id, Long userId, Long inputFileId, String prompt, String status, int progress,
                       Long resultFileId, String errorMessage) {
    }

    public Long create(Long userId, Long inputFileId, String prompt) {
        var entity = new AiTaskEntity();
        entity.setUserId(userId);
        entity.setInputFileId(inputFileId);
        entity.setPrompt(prompt);
        entity.setStatus("QUEUED");
        entity.setProgress(0);
        mapper.insert(entity);
        return entity.getId();
    }

    public void running(Long id) {
        // 中文：只更新状态字段，避免覆盖并发流程可能已经写入的其他列。
        // English: Update only the status fields so concurrent workflow changes to other columns are preserved.
        mapper.update(null, new UpdateWrapper<AiTaskEntity>()
                .eq("id", id)
                .set("status", "RUNNING")
                .set("progress", 10)
                .set("started_at", java.time.LocalDateTime.now()));
    }

    public void done(Long id, Long resultFileId) {
        mapper.update(null, new UpdateWrapper<AiTaskEntity>()
                .eq("id", id)
                .set("status", "COMPLETED")
                .set("progress", 100)
                .set("result_file_id", resultFileId)
                .set("completed_at", java.time.LocalDateTime.now()));
    }

    public void fail(Long id, String message) {
        mapper.update(null, new UpdateWrapper<AiTaskEntity>()
                .eq("id", id)
                .set("status", "FAILED")
                .set("error_message", message)
                .set("completed_at", java.time.LocalDateTime.now()));
    }

    public Task owned(Long id, Long userId) {
        // 中文：同时约束任务 ID 与用户 ID，确保用户只能读取自己的任务。
        // English: Constrain both task ID and user ID so users can only read their own tasks.
        var entity = mapper.selectOne(new QueryWrapper<AiTaskEntity>()
                .eq("id", id)
                .eq("user_id", userId));
        return entity == null ? null : new Task(entity.getId(), entity.getUserId(), entity.getInputFileId(),
                entity.getPrompt(), entity.getStatus(), entity.getProgress(), entity.getResultFileId(),
                entity.getErrorMessage());
    }
}
