package com.excelai.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.excelai.persistence.entity.AiTaskEntity;
import com.excelai.persistence.mapper.AiTaskMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public record HistoryInput(Long fileId, String name) { }
    public record HistoryTask(Long id, String status, int progress, String workflowType, String prompt,
                              String workflowOptions, LocalDateTime createdAt, LocalDateTime completedAt,
                              Long resultFileId, String errorMessage, String resultSummary,
                              List<HistoryInput> inputFiles) { }
    public record HistoryPage(List<HistoryTask> items, int page, int size, long total) { }

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

    public List<Long> queuedIds(int limit) {
        return jdbc.query("SELECT id FROM ai_task WHERE status='QUEUED' ORDER BY created_at,id LIMIT ?",
                (rs, rowNum) -> rs.getLong(1), limit);
    }

    public boolean claimQueued(Long id) {
        return jdbc.update("UPDATE ai_task SET status='RUNNING',progress=10,started_at=? WHERE id=? AND status='QUEUED'",
                LocalDateTime.now(), id) == 1;
    }

    public void requeue(Long id) {
        jdbc.update("UPDATE ai_task SET status='QUEUED',progress=0,started_at=NULL WHERE id=? AND status='RUNNING'", id);
    }

    public void recoverRunning() {
        jdbc.update("UPDATE ai_task SET status='QUEUED',progress=0,started_at=NULL WHERE status='RUNNING'");
    }

    public Task findById(Long id) {
        var entity = mapper.selectById(id);
        return entity == null ? null : toTask(entity);
    }

    public void done(Long id, Long resultFileId) {
        mapper.update(null, new UpdateWrapper<AiTaskEntity>()
                .eq("id", id).set("status", "COMPLETED").set("progress", 100)
                .set("result_file_id", resultFileId).set("completed_at", LocalDateTime.now()));
    }

    public void fail(Long id, String message) {
        String safeMessage = message == null ? "Task failed" : message;
        if (safeMessage.length() > 2000) safeMessage = safeMessage.substring(0, 2000);
        mapper.update(null, new UpdateWrapper<AiTaskEntity>()
                .eq("id", id).set("status", "FAILED").set("progress", 100).set("error_message", safeMessage)
                .set("completed_at", LocalDateTime.now()));
    }

    public void saveSummary(Long id, String summaryJson) {
        mapper.update(null, new UpdateWrapper<AiTaskEntity>().eq("id", id).set("result_summary", summaryJson));
    }

    public boolean updateSummaryIfUnchanged(Long id, String expectedSummaryJson, String updatedSummaryJson) {
        if (expectedSummaryJson == null) {
            return jdbc.update("UPDATE ai_task SET result_summary=? WHERE id=? AND status='COMPLETED' AND result_summary IS NULL",
                    updatedSummaryJson, id) == 1;
        }
        return jdbc.update("UPDATE ai_task SET result_summary=? WHERE id=? AND status='COMPLETED' AND BINARY result_summary=BINARY ?",
                updatedSummaryJson, id, expectedSummaryJson) == 1;
    }

    public Task owned(Long id, Long userId) {
        var entity = mapper.selectOne(new QueryWrapper<AiTaskEntity>().eq("id", id).eq("user_id", userId));
        return entity == null ? null : toTask(entity);
    }

    public HistoryPage history(Long userId, int page, int size, String status, String workflowType,
                               LocalDate fromDate, LocalDate toDate) {
        var query = new QueryWrapper<AiTaskEntity>().eq("user_id", userId);
        var countQuery = new QueryWrapper<AiTaskEntity>().eq("user_id", userId);
        if (status != null) {
            query.eq("status", status);
            countQuery.eq("status", status);
        }
        if (workflowType != null) {
            query.eq("workflow_type", workflowType);
            countQuery.eq("workflow_type", workflowType);
        }
        if (fromDate != null) {
            query.ge("created_at", fromDate.atStartOfDay());
            countQuery.ge("created_at", fromDate.atStartOfDay());
        }
        if (toDate != null) {
            query.lt("created_at", toDate.plusDays(1).atStartOfDay());
            countQuery.lt("created_at", toDate.plusDays(1).atStartOfDay());
        }
        long total = mapper.selectCount(countQuery);
        long offset = (long) page * size;
        List<AiTaskEntity> entities = mapper.selectList(query.orderByDesc("created_at", "id")
                .last("LIMIT " + size + " OFFSET " + offset));
        if (entities.isEmpty()) return new HistoryPage(List.of(), page, size, total);

        List<Long> taskIds = entities.stream().map(AiTaskEntity::getId).toList();
        String placeholders = String.join(",", java.util.Collections.nCopies(taskIds.size(), "?"));
        Object[] parameters = new Object[taskIds.size() + 1];
        parameters[0] = userId;
        System.arraycopy(taskIds.toArray(), 0, parameters, 1, taskIds.size());
        Map<Long, List<HistoryInput>> inputsByTask = new HashMap<>();
        org.springframework.jdbc.core.RowCallbackHandler inputHandler = rs -> inputsByTask.computeIfAbsent(rs.getLong("task_id"), ignored -> new ArrayList<>())
                .add(new HistoryInput(rs.getLong("id"), rs.getString("original_name")));
        jdbc.query("SELECT i.task_id, f.id, f.original_name FROM ai_task_input i "
                        + "JOIN ai_task t ON t.id=i.task_id AND t.user_id=? "
                        + "JOIN user_file f ON f.id=i.file_id AND f.user_id=t.user_id WHERE i.task_id IN (" + placeholders + ") "
                        + "ORDER BY i.task_id, i.ordinal",
                inputHandler, parameters);

        List<HistoryTask> items = entities.stream().map(entity -> new HistoryTask(entity.getId(), entity.getStatus(),
                entity.getProgress(), entity.getWorkflowType(), entity.getPrompt(), entity.getWorkflowOptions(),
                entity.getCreatedAt(), entity.getCompletedAt(), entity.getResultFileId(), entity.getErrorMessage(),
                entity.getResultSummary(), List.copyOf(inputsByTask.getOrDefault(entity.getId(), List.of())))).toList();
        return new HistoryPage(items, page, size, total);
    }

    private Task toTask(AiTaskEntity entity) {
        return new Task(entity.getId(), entity.getUserId(), entity.getInputFileId(),
                entity.getPrompt(), entity.getWorkflowType(), entity.getWorkflowOptions(), entity.getStatus(),
                entity.getProgress(), entity.getResultFileId(), entity.getErrorMessage(), entity.getResultSummary());
    }
}
