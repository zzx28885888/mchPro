package com.excelai.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 中文：plan 套餐配置表映射。planCode 是业务主键，不是自增数值 ID。
 * English: Mapping for plan configuration; planCode is the business key, not an auto-incrementing ID.
 */
@TableName("plan")
public class PlanEntity {
    @TableId(value = "plan_code", type = IdType.INPUT)
    private String planCode;
    private String planName;
    private int monthlyTasks;
    private long maxFileSize;
    private int maxToolCalls;
    private long toolTimeoutMs;

    public String getPlanCode() {
        return planCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public int getMonthlyTasks() {
        return monthlyTasks;
    }

    public void setMonthlyTasks(int monthlyTasks) {
        this.monthlyTasks = monthlyTasks;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    public void setMaxToolCalls(int maxToolCalls) {
        this.maxToolCalls = maxToolCalls;
    }

    public long getToolTimeoutMs() {
        return toolTimeoutMs;
    }

    public void setToolTimeoutMs(long toolTimeoutMs) {
        this.toolTimeoutMs = toolTimeoutMs;
    }
}
