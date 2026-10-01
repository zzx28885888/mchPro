package com.excelai.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.excelai.persistence.entity.AiTaskEntity;
import com.excelai.persistence.mapper.AiTaskMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 中文：ai_task 与输入文件关联的数据访问层。
 * English: Persistence adapter for ai_task and its ordered input-file links.
 */
@Repository
public class TaskRepository {
    private final AiTaskMapper mapper;
    private final JdbcTemplate jdbc;

    public TaskRepository(AiTaskMapper mapper, JdbcTemplate jdbc) {
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    public record Task(Long id, Long userId, Long inputFileId, String prompt, String workflowType,
                       String workflowOptions, String status, int progress, Long resultFileId,
                       String errorMessage, String resultSummary) { }

    public Long create(Long userId, Long inputFileId, String prompt) {
        return create(userId, List.of(inputFileId), "FREEFORM", "{}", prompt);
    }

    @Transactional
    public Long create(Long userId, List<Long> inputFileIds, String workflowType, String optionsJson, String prompt) {
        var entity = new AiTaskEntity();
        entity.setUserId(userId);
        entity.setInputFileId(inputFileIds.get(0));
        entity.setPrompt(prompt);
        entity.setWorkflowType(workflowType);
        entity.setWorkflowOptions(optionsJson);
        entity.setStatus("QUEUED");
        entity.setProgress(0);
        mapper.insert(entity);
        for (int ordinal = 0; ordinal < inputFileIds.size(); ordinal++) {
            jdbc.update("INSERT INTO ai_task_input(task_id,file_id,ordinal) VALUES(?,?,?)",
                    entity.getId(), inputFileIds.get(ordinal), ordinal);
        }
        return entity.getId();
    }

    public List<Long> inputFileIds(Long taskId) {
        List<Long> ids = jdbc.query("SELECT file_id FROM ai_task_input WHERE task_id=? ORDER BY ordinal",
                (rs, rowNum) -> rs.getLong(1), taskId);
        if (!ids.isEmpty()) return ids;
        AiTaskEntity task = mapper.selectById(taskId);
        return task == null || task.getInputFileId() == null ? List.of() : List.of(task.getInputFileId());
    }

    public void running(Long id) {
        mapper.update(null, new UpdateWrapper<AiTaskEntity>()
                .eq("id", id).set("status", "RUNNING").set("progress", 10)
                .set("started_at", LocalDateTime.now()));
    }

    public void done(Long id, Long resultFileId) {
        mapper.update(null, new UpdateWrapper<AiTaskEntity>()
                .eq("id", id).set("status", "COMPLETED").set("progress", 100)
                .set("result_file_id", resultFileId).set("completed_at", LocalDateTime.now()));
    }

    public void fail(Long id, String message) {
        mapper.update(null, new UpdateWrapper<AiTaskEntity>()
                .eq("id", id).set("status", "FAILED").set("error_message", message)
                .set("completed_at", LocalDateTime.now()));
    }

    public void saveSummary(Long id, String summaryJson) {
        mapper.update(null, new UpdateWrapper<AiTaskEntity>().eq("id", id).set("result_summary", summaryJson));
    }

    public Task owned(Long id, Long userId) {
        var entity = mapper.selectOne(new QueryWrapper<AiTaskEntity>().eq("id", id).eq("user_id", userId));
        return entity == null ? null : new Task(entity.getId(), entity.getUserId(), entity.getInputFileId(),
                entity.getPrompt(), entity.getWorkflowType(), entity.getWorkflowOptions(), entity.getStatus(),
                entity.getProgress(), entity.getResultFileId(), entity.getErrorMessage(), entity.getResultSummary());
    }
}