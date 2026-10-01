package com.excelai.excel;

import com.example.agent.tool.annotation.AgentTool;
import com.example.agent.tool.service.AgentToolContext;
import com.excelai.file.FileRepository;
import com.excelai.file.FileStorageService;
import com.excelai.task.TaskDataService;
import com.excelai.task.TaskRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.*;

/** 中文：只对当前可信任务绑定的输入文件执行确定性 Excel 工作流。English: Runs deterministic Excel workflows only on files bound to the trusted current task. */
@Component
public class ExcelWorkflowTools {
    private final TaskDataService data;
    private final TaskRepository tasks;
    private final FileRepository files;
    private final FileStorageService storage;
    private final ExcelService excel;
    private final ExcelWorkflowService workflows;
    private final ObjectMapper mapper;

    public ExcelWorkflowTools(TaskDataService data, TaskRepository tasks, FileRepository files,
                             FileStorageService storage, ExcelService excel, ExcelWorkflowService workflows, ObjectMapper mapper) {
        this.data = data;
        this.tasks = tasks;
        this.files = files;
        this.storage = storage;
        this.excel = excel;
        this.workflows = workflows;
        this.mapper = mapper;
    }

    @AgentTool(name = "inspect_workflow_inputs", description = "Inspect the trusted task workbooks and their worksheet schemas before applying a workflow.", permission = "excel:inspect_workflow_inputs", timeoutMs = 30000)
    public Map<String, Object> inspectInputs() {
        Long uid = number("userId"), taskId = number("taskId");
        List<Map<String, Object>> inspected = new ArrayList<>();
        for (Long fileId : inputFileIds()) {
            var file = ownedFile(fileId, uid);
            try {
                inspected.add(Map.of("fileName", file.originalName(), "sheets", excel.inspectWorkbook(storage.path(file.storagePath()))));
            } catch (Exception e) {
                throw new IllegalStateException("Cannot inspect workflow input", e);
            }
        }
        data.markInputsInspected(taskId);
        return Map.of("workflowType", contextWorkflow(), "inputs", inspected);
    }

    @AgentTool(name = "merge_clean_workbooks", description = "Merge and clean the selected trusted workbooks using only the confirmed task options.", permission = "excel:merge_clean_workbooks", timeoutMs = 60000)
    public Map<String, Object> mergeClean() {
        requireWorkflow("MERGE_CLEAN");
        requireInspected();
        Map<String, Object> options = options();
        List<Long> ids = inputFileIds();
        List<?> selectedSheets = list(options.get("sheetNames"));
        if (!selectedSheets.isEmpty() && selectedSheets.size() != ids.size()) throw new IllegalArgumentException("Choose exactly one worksheet per input file");
        List<SheetInput> inputs = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            var file = ownedFile(ids.get(i), number("userId"));
            ExcelWorkbook workbook = readWorkbook(file.storagePath());
            ExcelWorkbook.Sheet sheet = chooseSheet(workbook, selectedSheets.size() > i ? String.valueOf(selectedSheets.get(i)) : null);
            inputs.add(new SheetInput(file.id(), sheet.name(), sheet.headers(), sheet.rows()));
        }
        Map<String, List<String>> aliases = stringListMap(options.get("headerAliases"));
        List<String> duplicateKeys = strings(options.get("duplicateKeyColumns"));
        Map<String, String> dateFormats = stringMap(options.get("dateFormats"));
        Set<String> amounts = new LinkedHashSet<>(strings(options.get("amountColumns")));
        WorkflowResult result = workflows.mergeAndClean(inputs, new MergeOptions(aliases, duplicateKeys,
                !Boolean.FALSE.equals(options.get("skipBlankRows")), dateFormats, amounts));
        data.setWorkflowResult(number("taskId"), result);
        return resultSummary(result);
    }

    @AgentTool(name = "reconcile_workbooks", description = "Reconcile exactly two trusted workbooks with the confirmed keys, fields, and tolerance.", permission = "excel:reconcile_workbooks", timeoutMs = 60000)
    public Map<String, Object> reconcile() {
        requireWorkflow("RECONCILE");
        requireInspected();
        List<Long> ids = inputFileIds();
        if (ids.size() != 2) throw new IllegalArgumentException("Reconciliation requires two input files");
        Map<String, Object> options = options();
        ExcelWorkbook.Sheet left = selectedSheet(ids.get(0), number("userId"), text(options.get("leftSheetName")));
        ExcelWorkbook.Sheet right = selectedSheet(ids.get(1), number("userId"), text(options.get("rightSheetName")));
        SheetInput leftInput = new SheetInput(ids.get(0), left.name(), left.headers(), left.rows());
        SheetInput rightInput = new SheetInput(ids.get(1), right.name(), right.headers(), right.rows());
        WorkflowResult result = workflows.reconcile(leftInput, rightInput, new ReconcileOptions(
                stringMap(options.get("leftKeyMappings")), stringMap(options.get("rightKeyMappings")),
                stringMap(options.get("leftValueMappings")), stringMap(options.get("rightValueMappings")), decimal(options.get("tolerance"))));
        data.setWorkflowResult(number("taskId"), result);
        return resultSummary(result);
    }

    @AgentTool(name = "summarize_workbook", description = "Build a deterministic grouped summary from the trusted workbook and confirmed options.", permission = "excel:summarize_workbook", timeoutMs = 60000)
    public Map<String, Object> summarize() {
        requireWorkflow("SUMMARY");
        requireInspected();
        List<Long> ids = inputFileIds();
        if (ids.size() != 1) throw new IllegalArgumentException("Summary requires one input file");
        Map<String, Object> options = options();
        ExcelWorkbook.Sheet sheet = selectedSheet(ids.get(0), number("userId"), text(options.get("sheetName")));
        SheetInput input = new SheetInput(ids.get(0), sheet.name(), sheet.headers(), sheet.rows());
        WorkflowResult result = workflows.summarize(input, new SummaryOptions(text(options.get("measureColumn")),
                strings(options.get("dimensionColumns")), text(options.get("aggregation")), text(options.get("dateColumn")),
                text(options.get("datePeriod")), text(options.get("dateFormat"))));
        data.setWorkflowResult(number("taskId"), result);
        return resultSummary(result);
    }

    @AgentTool(name = "export_workbook_result", description = "Export the successful deterministic workflow result as a new workbook and persist its audit summary.", permission = "excel:export_workbook_result", timeoutMs = 30000)
    public Map<String, Object> export() throws Exception {
        Long taskId = number("taskId"), userId = number("userId");
        if (data.result(taskId) != null) throw new IllegalStateException("Workflow result was already exported");
        WorkflowResult result = data.workflowResult(taskId);
        if (result == null) throw new IllegalStateException("Run the deterministic workflow successfully before export");
        String workflow = contextWorkflow();
        String name = "ai-" + workflow.toLowerCase(Locale.ROOT) + "-result-" + taskId + ".xlsx";
        Path path = storage.savePathName(name);
        excel.writeWorkbook(path, result.outputSheets());
        Long fileId = files.insert(userId, name, path.getFileName().toString(), path.toString(), Files.size(path),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "OUTPUT");
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("workflowType", workflow);
        summary.put("operationDefinition", options());
        summary.put("counts", result.counts());
        summary.put("warnings", result.warnings());
        summary.put("exceptions", result.exceptions());
        summary.put("outputSheets", result.outputSheets().stream().map(sheet -> Map.of("name", sheet.name(), "rows", sheet.rows().size())).toList());
        tasks.saveSummary(taskId, mapper.writeValueAsString(summary));
        data.setResult(taskId, fileId);
        return Map.of("fileId", fileId, "sheets", result.outputSheets().size(), "counts", result.counts());
    }

    private Map<String, Object> resultSummary(WorkflowResult result) {
        return Map.of("counts", result.counts(), "warnings", result.warnings(), "exceptionCount", result.exceptions().size(), "exceptionSamples", result.exceptions().stream().limit(8).toList(),
                "outputSheets", result.outputSheets().stream().map(sheet -> Map.of("name", sheet.name(), "rows", sheet.rows().size())).toList());
    }

    private ExcelWorkbook readWorkbook(String storedPath) {
        try { return excel.readWorkbook(storage.path(storedPath)); }
        catch (Exception e) { throw new IllegalStateException("Cannot read workflow input", e); }
    }

    private ExcelWorkbook.Sheet selectedSheet(Long fileId, Long userId, String name) {
        var file = ownedFile(fileId, userId);
        return chooseSheet(readWorkbook(file.storagePath()), name);
    }

    private ExcelWorkbook.Sheet chooseSheet(ExcelWorkbook workbook, String name) {
        if (name != null && !name.isBlank()) return workbook.sheets().stream().filter(sheet -> sheet.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Selected worksheet does not exist: " + name));
        if (workbook.sheets().size() != 1) throw new IllegalArgumentException("Select a worksheet explicitly when a workbook has multiple sheets");
        return workbook.sheets().get(0);
    }

    private FileRepository.FileRecord ownedFile(Long fileId, Long userId) {
        var file = files.findOwned(fileId, userId);
        if (file == null) throw new SecurityException("Workflow input file is unavailable");
        return file;
    }

    private List<Long> inputFileIds() {
        Object value = AgentToolContext.get().get("inputFileIds");
        if (!(value instanceof List<?> list) || list.isEmpty()) throw new SecurityException("Trusted task inputs are missing");
        List<Long> ids = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Number number)) throw new SecurityException("Trusted task inputs are invalid");
            ids.add(number.longValue());
        }
        return List.copyOf(ids);
    }

    private void requireInspected() {
        if (!data.inputsInspected(number("taskId"))) throw new IllegalStateException("Inspect workflow inputs before transformation");
    }

    private void requireWorkflow(String expected) {
        if (!expected.equals(contextWorkflow())) throw new SecurityException("Tool does not match the selected workflow");
    }

    private String contextWorkflow() { return Objects.toString(AgentToolContext.get().get("workflowType"), "FREEFORM"); }
    private Map<String, Object> options() {
        Object value = AgentToolContext.get().get("options");
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }
    private Long number(String key) { Object value = AgentToolContext.get().get(key); return value instanceof Number n ? n.longValue() : null; }
    private String text(Object value) { return value == null ? null : String.valueOf(value); }
    private List<?> list(Object value) { return value instanceof List<?> list ? list : List.of(); }
    private List<String> strings(Object value) { return list(value).stream().map(String::valueOf).toList(); }
    private Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), String.valueOf(item)));
        return result;
    }
    private Map<String, List<String>> stringListMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, List<String>> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), strings(item)));
        return result;
    }
    private BigDecimal decimal(Object value) { return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value)); }
}
