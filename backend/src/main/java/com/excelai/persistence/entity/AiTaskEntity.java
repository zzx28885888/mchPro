package com.excelai.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 中文：ai_task 表映射，表示一次用户提交的异步 Excel Agent 作业。
 * English: Mapping for ai_task, representing one asynchronous Excel Agent job submitted by a user.
 */
@TableName("ai_task")
public class AiTaskEntity {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long inputFileId;
    private String prompt;
    private String status;
    private int progress;
    private Long resultFileId;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getInputFileId() {
        return inputFileId;
    }

    public void setInputFileId(Long inputFileId) {
        this.inputFileId = inputFileId;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public Long getResultFileId() {
        return resultFileId;
    }

    public void setResultFileId(Long resultFileId) {
        this.resultFileId = resultFileId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
