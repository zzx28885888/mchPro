# CSV 导入与导出实施计划 / CSV Import and Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
> **代理执行要求：** 必须使用 `superpowers:executing-plans` 子技能逐项执行本计划。步骤使用复选框（`- [ ]`）跟踪。

**Goal:** 将 CSV 输入接入现有工作流，并为摘要与合并清洗提供可选 CSV 下载。
**目标：** Integrate CSV input into existing workflows and offer CSV downloads for summary and merge/cleanup.

**Architecture:** Add a dependency-free CSV parser/encoder that maps CSV to the existing one-sheet `ExcelWorkbook` model. Dispatch upload validation and workbook reads by file extension, validate output format in the task service, and route deterministic export to CSV or XLSX while keeping reconciliation and freeform output as XLSX.
**架构：** 新增无第三方依赖的 CSV 解析/编码器，将 CSV 映射为现有单工作表 `ExcelWorkbook`。上传校验和工作簿读取按扩展名分派；任务服务校验输出格式；确定性工作流按选择写出 CSV 或 XLSX，对账与自由模式仍输出 XLSX。

**Tech Stack:** Java 21, Spring Boot, existing EasyExcel/POI workbook model, JUnit/Mockito, static HTML/JavaScript.
**技术栈：** Java 21、Spring Boot、现有 EasyExcel/POI 工作簿模型、JUnit/Mockito、静态 HTML/JavaScript。

**Spec:** `docs/superpowers/specs/2026-10-19-csv-import-export-design.md`
**规格：** `docs/superpowers/specs/2026-10-19-csv-import-export-design.md`

## Global Constraints / 全局约束

- Accept `.xlsx` and `.csv`; map CSV to one sheet named `csv_data`. / 接受 `.xlsx` 和 `.csv`；CSV 映射为名为 `csv_data` 的单工作表。
- Input encodings are strict UTF-8 with or without BOM, then GB18030 fallback; without a BOM, prefer the valid decode with more Han characters and prefer UTF-8 on a tie. Output is UTF-8 with BOM and comma-delimited. / 输入编码先严格尝试带或不带 BOM 的 UTF-8，再回退到 GB18030；没有 BOM 且两种解码都有效时选择汉字更多的结果，平局选 UTF-8。输出为带 BOM 的 UTF-8 逗号分隔文件。
- Detect comma, tab, or semicolon; support quoted fields, escaped quotes, and embedded newlines. / 自动识别逗号、制表符或分号；支持引号字段、转义引号和字段内换行。
- Do not add a third-party CSV dependency. / 不新增 CSV 第三方依赖。
- Reuse existing file-size, plan, aggregate-size, ownership, and preview-fingerprint checks. / 复用现有文件大小、套餐、累计大小、归属和预览指纹校验。
- CSV export is valid only for `SUMMARY` and `MERGE_CLEAN`; default output remains XLSX. / 仅 `SUMMARY` 与 `MERGE_CLEAN` 可导出 CSV；默认仍为 XLSX。
- Do not echo uploaded file contents in errors; do not silently discard nonempty fields or columns. / 错误信息不得回显上传内容；不得静默丢弃非空字段或列。
- Defer manual UI acceptance as requested; record it as remaining work in the bilingual roadmap. / 按用户要求暂缓手动 UI 验收，并在双语路线图中保留为待办。

## Review Focus / 重点审查输入

1. UTF-8 BOM and GB18030 files must preserve Chinese headers and values. Pin with `CsvServiceTest.readsUtf8BomAndGb18030`. / UTF-8 BOM 与 GB18030 文件必须保留中文表头和数据；由 `CsvServiceTest.readsUtf8BomAndGb18030` 固定行为。
2. Quoted delimiters, escaped quotes, and embedded newlines must stay inside their fields. Pin with `CsvServiceTest.parsesQuotedDelimitersEscapesAndNewlines`. / 被引号包裹的分隔符、转义引号和字段内换行必须留在字段中；由 `CsvServiceTest.parsesQuotedDelimitersEscapesAndNewlines` 固定行为。
3. Inconsistent field counts or malformed quoting must fail clearly without truncating values. Pin with `CsvServiceTest.rejectsMalformedRowsAndQuotes`. / 列数不一致或引号格式错误必须明确失败且不得截断数据；由 `CsvServiceTest.rejectsMalformedRowsAndQuotes` 固定行为。
4. CSV output selection must be bound to preview and rejected for unsupported workflows. Pin with `TaskServiceTest.rejectsCsvOutputForUnsupportedWorkflowAndFingerprintMismatch`. / CSV 输出选择必须绑定预览，且不支持的工作流必须拒绝；由 `TaskServiceTest.rejectsCsvOutputForUnsupportedWorkflowAndFingerprintMismatch` 固定行为。
5. CSV result filenames, MIME type, BOM, and escaping must agree. Pin with `CsvServiceTest.writesUtf8BomCsvWithEscapedValues`, `ExcelWorkflowToolsTest.exportsSummaryAndMergeAsCsv`, and `ExcelWorkflowToolsTest.keepsXlsxDefaultExport`. / CSV 结果文件名、MIME、BOM 和转义必须一致；由 `CsvServiceTest.writesUtf8BomCsvWithEscapedValues`、`ExcelWorkflowToolsTest.exportsSummaryAndMergeAsCsv` 和 `ExcelWorkflowToolsTest.keepsXlsxDefaultExport` 固定行为。

---

### Task 1: Add a bounded CSV codec / 任务 1：新增有界 CSV 编解码器

**Files / 文件：**
- Create: `backend/src/main/java/com/excelai/excel/CsvService.java`
- Test: `backend/src/test/java/com/excelai/excel/CsvServiceTest.java`

**Interfaces / 接口：**
- Produces `ExcelWorkbook.Sheet read(Path path)`, `ExcelWorkbook.Sheet read(byte[] content)`, `void validate(byte[] content)`, and `void write(Path path, OutputSheet sheet)`. / 提供 `ExcelWorkbook.Sheet read(Path path)`、`ExcelWorkbook.Sheet read(byte[] content)`、`void validate(byte[] content)` 和 `void write(Path path, OutputSheet sheet)`。
- The logical sheet name is `csv_data`; parsing returns headers and string rows without numeric or formula coercion. / 逻辑工作表名为 `csv_data`；解析结果保留字符串表头/数据，不做数值或公式转换。

- [ ] Write failing tests named `readsUtf8BomAndGb18030`, `detectsCommaTabAndSemicolon`, `parsesQuotedDelimitersEscapesAndNewlines`, `rejectsEmptyHeaderMalformedRowsAndQuotes`, and `writesUtf8BomCsvWithEscapedValues`. / 编写上述命名的失败测试，覆盖两种编码、三类分隔符、引号/换行、无效输入和正确输出转义。
- [ ] Run `mvn -q -f backend/pom.xml -Dtest=CsvServiceTest test` and confirm the new behavior fails before implementation. / 运行该命令，确认实现前新行为失败。
- [ ] Implement `CsvService` without third-party dependencies: strip UTF-8 BOM; strictly decode UTF-8 and GB18030, prefer UTF-8 when a BOM is present, and otherwise prefer the valid decode with more Han characters (UTF-8 on ties); choose the delimiter with the most consistent nonzero field counts among comma/tab/semicolon, preferring wider rows on ties; parse quoted fields, escaped quotes, and embedded newlines; reject empty headers, malformed quotes, and rows with inconsistent column counts. / 实现无第三方依赖的 `CsvService`：去除 UTF-8 BOM；严格尝试 UTF-8 和 GB18030，带 BOM 时选择 UTF-8，不带 BOM 时优先选择汉字更多的有效解码结果（平局选 UTF-8）；在逗号/制表符/分号中选择非零列数且一致性最高的分隔符，平局优先选择列数更多者；解析引号字段、转义引号和字段内换行；拒绝空表头、非法引号和列数不一致的行。
- [ ] Run the same focused Maven command and confirm all `CsvServiceTest` cases pass. / 再运行该 Maven 命令并确认 `CsvServiceTest` 全部通过。

### Task 2: Accept CSV uploads and dispatch workbook reads / 任务 2：接受 CSV 上传并分派工作簿读取

**Files / 文件：**
- Modify: `backend/src/main/java/com/excelai/file/FileController.java`
- Modify: `backend/src/main/java/com/excelai/excel/ExcelService.java`
- Modify: `backend/src/main/java/com/excelai/task/TaskService.java`
- Test: `backend/src/test/java/com/excelai/file/FileControllerTest.java` (create if absent)
- Test: `backend/src/test/java/com/excelai/excel/ExcelServiceTest.java`
- Test: `backend/src/test/java/com/excelai/task/TaskServiceTest.java`

**Interfaces / 接口：**
- Inject `CsvService` into `FileController`; accept only a case-insensitive `.xlsx` or `.csv` filename, run existing XLSX validation for XLSX, and validate CSV bytes only after plan-size validation. / `FileController` 注入 `CsvService`；只接受大小写不敏感的 `.xlsx` 或 `.csv`；XLSX 继续走原验证；CSV 在套餐大小校验后读取并校验。
- Add `ExcelService.readWorkbook(Path)` and `inspectWorkbook(Path)` extension dispatch; XLSX behavior remains unchanged, CSV returns exactly one `csv_data` sheet/schema. / `ExcelService.readWorkbook(Path)` 与 `inspectWorkbook(Path)` 按扩展名分派；XLSX 行为不变，CSV 返回且只返回一个 `csv_data` 工作表/结构。

- [ ] Write failing upload tests `acceptsValidCsvAndXlsx`, `rejectsUnsupportedExtensionAndInvalidCsv`, and `checksPlanSizeBeforeReadingCsv`; assert persisted original filename, size, and owner remain unchanged. / 编写上述上传失败测试，并断言保存的原文件名、大小和所有者保持正确。
- [ ] Run `mvn -q -f backend/pom.xml -Dtest=FileControllerTest test` and confirm expected failures. / 运行该命令并确认测试先失败。
- [ ] Implement extension dispatch in `FileController`; retain current auth, user ownership, file-size, save, and cleanup behavior. / 在 `FileController` 实现扩展名分派，并保留现有认证、用户归属、大小、存储和清理行为。
- [ ] Write failing `ExcelServiceTest.readsCsvAsSingleLogicalSheetAndInspectsItsHeaders`; retain the existing XLSX reader tests as regression coverage. / 编写 `ExcelServiceTest.readsCsvAsSingleLogicalSheetAndInspectsItsHeaders` 失败用例；保留现有 XLSX 读取测试作为回归覆盖。
- [ ] Implement CSV dispatch through `CsvService`, with XLSX code paths untouched. / 通过 `CsvService` 实现 CSV 分派，保持 XLSX 代码路径不变。
- [ ] Run `mvn -q -f backend/pom.xml -Dtest=FileControllerTest,ExcelServiceTest test` and confirm both suites pass. / 运行该命令并确认两组测试通过。

### Task 3: Validate export format against workflow and preview / 任务 3：按工作流与预览校验导出格式

**Files / 文件：**
- Modify: `backend/src/main/java/com/excelai/task/TaskService.java`
- Test: `backend/src/test/java/com/excelai/task/TaskServiceTest.java`

**Interfaces / 接口：**
- `options.outputFormat` accepts only `XLSX` or `CSV`; missing value normalizes to `XLSX`. `CSV` is accepted only for `SUMMARY` and `MERGE_CLEAN`. / `options.outputFormat` 仅接受 `XLSX` 或 `CSV`；缺省规范化为 `XLSX`；只有 `SUMMARY` 与 `MERGE_CLEAN` 接受 `CSV`。
- The normalized option remains part of the existing request digest and serialized task options. / 规范化后的选项继续纳入现有请求摘要和任务选项序列化。

- [ ] Write failing tests `defaultsOutputFormatToXlsx`, `acceptsCsvForSummaryAndMerge`, `rejectsCsvForReconcileAndFreeform`, and `rejectsCsvOutputForUnsupportedWorkflowAndFingerprintMismatch`. / 编写上述失败测试，覆盖缺省、支持工作流、不支持工作流和预览指纹不匹配。
- [ ] Run `mvn -q -f backend/pom.xml -Dtest=TaskServiceTest test` and confirm expected failures. / 运行该命令并确认测试先失败。
- [ ] Implement an output-format normalization/validation helper used by `preview` and `createInternal`; preserve existing preview digest and options persistence. / 实现供 `preview` 与 `createInternal` 共用的输出格式规范化/校验方法，并保留现有指纹和选项持久化逻辑。
- [ ] Run the focused TaskService test command and confirm all cases pass. / 运行 TaskService 定向测试并确认全部通过。

### Task 4: Export deterministic results as CSV / 任务 4：确定性工作流结果导出 CSV

**Files / 文件：**
- Modify: `backend/src/main/java/com/excelai/excel/ExcelWorkflowTools.java`
- Modify: `backend/src/main/java/com/excelai/excel/ExcelService.java`
- Test: `backend/src/test/java/com/excelai/excel/ExcelWorkflowToolsTest.java` (create if absent)
- Test: `backend/src/test/java/com/excelai/excel/ExcelServiceTest.java`

**Interfaces / 接口：**
- `ExcelWorkflowTools.export()` selects `CsvService.write(path, outputSheet)` for normalized `CSV`, writes one `.csv` output, and persists `text/csv; charset=UTF-8`; otherwise it retains current XLSX export and MIME type. / `ExcelWorkflowTools.export()` 遇到规范化的 `CSV` 时调用 `CsvService.write(path, outputSheet)`，写出一个 `.csv` 并保存 `text/csv; charset=UTF-8`；其他情况保留现有 XLSX 写出和 MIME 类型。
- Reject CSV export if output sheet count is not exactly one, even though current supported workflows produce one sheet. / 即使当前支持的工作流只产生单表，若结果工作表数量不为 1 仍拒绝 CSV 导出。

- [ ] Write failing tests `exportsSummaryAndMergeAsCsv`, `rejectsCsvForMultipleOutputSheets`, and `keepsXlsxDefaultExport`. / 编写上述失败测试，覆盖 CSV 汇总/合并、意外多表拒绝和默认 XLSX。
- [ ] Run `mvn -q -f backend/pom.xml -Dtest=ExcelWorkflowToolsTest,ExcelServiceTest test` and confirm expected failures. / 运行该命令并确认实现前预期失败。
- [ ] Implement export-format selection, extension/MIME selection, single-sheet guard, and CSV writer call; keep summary metadata and task result registration unchanged. / 实现格式选择、扩展名/MIME 选择、单表检查及 CSV 写入；保持摘要元数据和任务结果登记不变。
- [ ] Run the focused Excel tool/service test command and confirm all cases pass. / 运行 Excel 工具/服务定向测试并确认全部通过。

### Task 5: Add UI file-format and output-format choices / 任务 5：增加界面输入与输出格式选择

**Files / 文件：**
- Modify: `backend/src/main/resources/static/index.html`
- Test: `backend/workflow-templates.test.mjs` (only if a reusable UI option helper is extracted)

**Interfaces / 接口：**
- File input accepts `.xlsx,.csv`; upload copy describes both formats in Chinese and English. / 文件选择接受 `.xlsx,.csv`；上传说明中英双语描述两种格式。
- Add an output format selector for typed `SUMMARY` and `MERGE_CLEAN` workflows only; default to XLSX, hide or disable for `RECONCILE` and `FREEFORM`, and include the value in both preview and confirmed task request options. / 仅为结构化 `SUMMARY` 与 `MERGE_CLEAN` 提供输出格式选项，默认 XLSX；对 `RECONCILE` 与 `FREEFORM` 隐藏或禁用；预览请求和确认任务请求都要携带该选项。

- [ ] Inspect the existing option rendering, option collection, preview payload, and confirmed task payload in `index.html`; record the exact insertion points in the implementation diff. / 检查 `index.html` 中选项渲染、选项收集、预览载荷和确认任务载荷，并在实现差异中明确插入点。
- [ ] Implement bilingual upload text and conditional output selector; ensure changing the format invalidates the current preview through existing option-change handling. / 实现双语上传说明和条件输出选择器；确保修改格式后由现有选项变更逻辑清除预览。
- [ ] Run `node --check backend/src/main/resources/static/workflow-templates.mjs` and a syntax check of the inline module extracted from `index.html`; confirm no manual UI acceptance was performed. / 运行模块语法检查及从 `index.html` 提取的内联模块语法检查；明确未执行手动 UI 验收。

### Task 6: Record delivery and verify the branch / 任务 6：记录交付并验证分支

**Files / 文件：**
- Modify: `docs/superpowers/plans/2026-10-19-project-status-and-roadmap.md`
- Modify: `docs/superpowers/plans/2026-10-19-csv-import-export.md`

- [ ] Add a bilingual dated roadmap entry with implemented scope, automated verification evidence, deferred manual acceptance, and the next recommended feature. / 在路线图顶部增加双语日期记录，写明实现范围、自动化验证证据、暂缓的手动验收和下一项建议。
- [ ] Check off plan steps only when their implementation and associated verification are complete; leave manual acceptance explicitly deferred. / 仅在实现和对应验证完成后勾选计划步骤；明确保留手动验收待办。
- [ ] Run `mvn -q -f backend/pom.xml test`, `uv run pytest tests/test_workflow_tool_schemas.py` from `agent-service`, the JavaScript syntax checks above, and `git diff --check`; record exact results. / 运行后端全套 Maven 测试、在 `agent-service` 目录执行 `uv run pytest tests/test_workflow_tool_schemas.py`、上述 JavaScript 语法检查和 `git diff --check`，并记录精确结果。
- [ ] Review all changed documentation and comments for aligned Chinese and English, then inspect `git status --short` and `git diff --stat`. / 检查所有修改文档与注释的中英文一致性，再检查 `git status --short` 和 `git diff --stat`。
