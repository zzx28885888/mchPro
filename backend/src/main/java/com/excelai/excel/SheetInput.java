package com.excelai.excel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 中文：不可变工作表输入及其可信来源信息。English: Immutable sheet input with trusted source provenance. */
public record SheetInput(Long fileId, String sheetName, List<String> headers, List<List<String>> rows) {
    public SheetInput {
        headers = List.copyOf(headers);
        rows = rows.stream().map(row -> Collections.unmodifiableList(new ArrayList<>(row))).toList();
    }

    /** 中文：按原始列位置读取值，缺失单元格返回空字符串。English: Reads a value by original column position and returns an empty string when absent. */
    public String value(List<String> row, int column) {
        return column < row.size() ? row.get(column) : "";
    }
}
