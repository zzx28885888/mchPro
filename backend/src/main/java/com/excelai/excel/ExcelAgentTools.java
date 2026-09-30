package com.excelai.excel;

import com.example.agent.tool.annotation.AgentTool;
import com.example.agent.tool.annotation.ToolParam;
import com.example.agent.tool.service.AgentToolContext;
import com.excelai.file.*;
import com.excelai.task.TaskDataService;
import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.util.*;

@Component
/**
 * 中文：V1.6 Java Tool Registry 的 Excel 业务工具。当前任务、用户与输入文件通过可信上下文取得。
 * English: Excel business tools registered with the V1.6 Java registry; task, user, and input-file IDs come from trusted context.
 */
public class ExcelAgentTools {
    private final TaskDataService data;
    private final FileRepository files;
    private final FileStorageService storage;
    private final ExcelService excel;

    public ExcelAgentTools(TaskDataService d, FileRepository f, FileStorageService s, ExcelService e) {
        data = d;
        files = f;
        storage = s;
        excel = e;
    }

    private Long task() {
        return num("taskId");
    }

    private Long user() {
        return num("userId");
    }

    private Long num(String key) {
        Object v = AgentToolContext.get().get(key);
        return v instanceof Number n ? n.longValue() : null;
    }

    @AgentTool(name = "read_excel", description = "Read the uploaded Excel workbook for this task. Call before filtering or sorting.", permission = "excel:read_excel", timeoutMs = 30000)
    public Map<String, Object> read() throws Exception {
        // 中文：必须根据任务绑定的 inputFileId 查询该用户拥有的文件，再读取私有存储中的工作簿。
        // English: Resolve the task-bound inputFileId through an ownership-scoped lookup before reading the private workbook.
        Long fileId = num("inputFileId");
        var f = files.findOwned(fileId, user());
        if (f == null) throw new SecurityException("input file unavailable");
        var rows = excel.read(storage.path(f.storagePath()));
        data.setRows(task(), rows);
        return Map.of("rows", rows.size(), "columns", rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet()), "preview", rows.stream().limit(5).toList());
    }

    @AgentTool(name = "filter", description = "Filter loaded Excel rows by a column and exact text value.", permission = "excel:filter", timeoutMs = 10000)
    public Map<String, Object> filter(@ToolParam(name = "column") String column, @ToolParam(name = "value") String value) {
        var rows = data.rows(task());
        rows.removeIf(r -> !Objects.toString(r.get(column), "").equalsIgnoreCase(value));
        data.setRows(task(), rows);
        return Map.of("remainingRows", rows.size());
    }

    @AgentTool(name = "sort", description = "Sort loaded Excel rows by a column. Set ascending=false for descending order.", permission = "excel:sort", timeoutMs = 10000)
    public Map<String, Object> sort(@ToolParam(name = "column") String column, @ToolParam(name = "ascending") Boolean ascending) {
        var rows = data.rows(task());
        rows.sort((a, b) -> {
            int c = Objects.toString(a.get(column), "").compareToIgnoreCase(Objects.toString(b.get(column), ""));
            return Boolean.FALSE.equals(ascending) ? -c : c;
        });
        data.setRows(task(), rows);
        return Map.of("sortedBy", column, "ascending", !Boolean.FALSE.equals(ascending));
    }

    @AgentTool(name = "top", description = "Keep the first N rows of the current Excel result.", permission = "excel:top", timeoutMs = 10000)
    public Map<String, Object> top(@ToolParam(name = "count") Integer count) {
        if (count < 1) throw new IllegalArgumentException("count must be positive");
        var rows = data.rows(task());
        if (rows.size() > count) data.setRows(task(), rows.subList(0, count));
        return Map.of("remainingRows", data.rows(task()).size());
    }

    @AgentTool(name = "export_excel", description = "Export the current Excel rows as a downloadable result workbook. Call this after analysis.", permission = "excel:export_excel", timeoutMs = 30000)
    public Map<String, Object> export() throws Exception {
        // 中文：结果先写入私有文件目录，再创建 OUTPUT 文件记录；任务 Worker 随后把文件 ID 记为任务结果。
        // English: Write the result to private storage, insert an OUTPUT file record, then let the task worker attach its ID.
        Long tid = task();
        String name = "ai-result-" + tid + ".xlsx";
        Path path = storage.savePathName(name);
        excel.write(path, data.rows(tid));
        long size = Files.size(path);
        Long id = files.insert(user(), name, path.getFileName().toString(), path.toString(), size, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "OUTPUT");
        data.setResult(tid, id);
        return Map.of("fileId", id, "rows", data.rows(tid).size());
    }
}
