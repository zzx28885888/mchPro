package com.excelai.task;

import java.util.Locale;

/** 中文：定义任务可选的业务工作流类型。English: Defines the supported business workflow types for a task. */
public enum WorkflowType {
    FREEFORM,
    MERGE_CLEAN,
    RECONCILE,
    SUMMARY;

    public static WorkflowType parse(String value) {
        if (value == null || value.isBlank()) return FREEFORM;
        return WorkflowType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}