package com.excelai.excel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 中文：按原始顺序保存工作簿中的工作表、表头和单元格行，避免重复或空表头丢失。
 * English: Preserves workbook sheet order, original headers, and cell rows so duplicate or blank headers are not lost.
 */
public record ExcelWorkbook(List<Sheet> sheets) {
    public ExcelWorkbook {
        sheets = List.copyOf(sheets);
    }

    /** 中文：单个工作表的原始结构。English: Original tabular structure for one worksheet. */
    public record Sheet(String name, List<String> headers, List<List<String>> rows) {
        public Sheet {
            headers = Collections.unmodifiableList(new ArrayList<>(headers));
            rows = rows.stream()
                    .map(row -> Collections.unmodifiableList(new ArrayList<>(row)))
                    .toList();
        }
    }
}