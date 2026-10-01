package com.excelai.excel;

import java.util.List;
import java.util.Map;

/** 中文：包含可导出工作表、汇总计数和逐行异常的工作流结果。English: Workflow output sheets, aggregate counts, and row-level exceptions. */
public record WorkflowResult(List<OutputSheet> outputSheets, Map<String, Long> counts,
                             List<String> warnings, List<RowException> exceptions) {
    public WorkflowResult {
        outputSheets = List.copyOf(outputSheets);
        counts = Map.copyOf(counts);
        warnings = List.copyOf(warnings);
        exceptions = List.copyOf(exceptions);
    }

    /** 中文：保留来源文件、工作表和一基行号的异常。English: Exception with source file, sheet, and one-based row number. */
    public record RowException(String code, Long fileId, String sheetName, int rowNumber, String column,
                               String message, String value) { }
}
