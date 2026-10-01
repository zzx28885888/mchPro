package com.excelai.excel;

import java.util.List;

/** 中文：待导出的有序工作表数据。English: Ordered worksheet data ready for export. */
public record OutputSheet(String name, List<String> headers, List<List<String>> rows) {
    public OutputSheet {
        headers = List.copyOf(headers);
        rows = rows.stream().map(List::copyOf).toList();
    }
}
