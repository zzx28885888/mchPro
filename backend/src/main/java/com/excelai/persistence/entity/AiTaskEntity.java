package com.excelai.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 中文：异步 Excel 任务的持久化映射。English: Persistence mapping for asynchronous Excel tasks. */
@TableName("ai_task")
public class AiTaskEntity {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long inputFileId;
    private String prompt;
    private String workflowType;
    private String workflowOptions;
    private String resultSummary;
    private String status;
    private int progress;
    private Long resultFileId;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getUserId() { return userId; }
    public void setUserId(Long value) { userId = value; }
    public Long getInputFileId() { return inputFileId; }
    public void setInputFileId(Long value) { inputFileId = value; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String value) { prompt = value; }
    public String getWorkflowType() { return workflowType; }
    public void setWorkflowType(String value) { workflowType = value; }
    public String getWorkflowOptions() { return workflowOptions; }
    public void setWorkflowOptions(String value) { workflowOptions = value; }
    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String value) { resultSummary = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public int getProgress() { return progress; }
    public void setProgress(int value) { progress = value; }
    public Long getResultFileId() { return resultFileId; }
    public void setResultFileId(Long value) { resultFileId = value; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String value) { errorMessage = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime value) { startedAt = value; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime value) { completedAt = value; }
}