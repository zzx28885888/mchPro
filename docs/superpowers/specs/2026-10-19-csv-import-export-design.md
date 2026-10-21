# CSV 导入与导出设计 / CSV Import and Export Design

**日期：** 2026-10-19
**Date:** 2026-10-19

## 目标 / Goal

让用户可以把 CSV 文件直接用于现有 Excel 工作流，并在适合单表输出的工作流中选择 CSV 下载。继续复用现有的文件归属、套餐大小限制、预览确认、异步任务和结果下载机制。
Allow users to use CSV files directly in the existing spreadsheet workflows and choose CSV downloads for workflows that produce a single logical table. Reuse the existing file ownership checks, plan size limits, preview confirmation, asynchronous tasks, and result downloads.

## 方案选择 / Chosen Approach

已评估三种方案：新增独立 CSV 转换页、全面替换所有工作流的读写格式，或把 CSV 纳入现有任务管线并保留 XLSX 作为复杂结果格式。选择第三种：改动集中在已有文件/工作簿边界，用户不需要学习另一套流程；多工作表和 Agent 自由输出继续保留 XLSX 语义。
Three approaches were considered: a separate CSV conversion page, replacing file handling across every workflow, or adding CSV to the existing task pipeline while retaining XLSX for complex results. The third option is selected: changes stay at existing file and workbook boundaries, users keep one workflow, and multi-sheet and freeform Agent outputs retain XLSX semantics.

## 支持范围 / Supported Scope

- 输入接受 `.xlsx` 与 `.csv`。XLSX 继续使用现有安全校验和工作簿解析；CSV 映射为一个名为 `csv_data` 的逻辑工作表，表头和数据行进入现有预览及工作流映射。
  Accept `.xlsx` and `.csv` inputs. XLSX keeps the existing security validation and workbook parser; CSV maps to one logical sheet named `csv_data`, with its headers and rows flowing into existing previews and workflow mappings.
- CSV 输入支持 UTF-8（有或无 BOM）以及常见中文 Windows 编码 GB18030。先严格解码 UTF-8，再尝试 GB18030；没有 BOM 且两种解码都有效时，优先选择汉字更多的解码结果，平局时选择 UTF-8；无法解码时拒绝文件并说明可接受的编码。
  CSV input supports UTF-8 (with or without BOM) and the common Chinese Windows encoding GB18030. Decode UTF-8 strictly first, then try GB18030; without a BOM, if both decoders accept the bytes, prefer the result with more Han characters and prefer UTF-8 on a tie. Reject undecodable files with an actionable encoding message.
- 自动从逗号、制表符、分号中选择字段数一致性最高的分隔符；遵循双引号包围字段、双引号转义和字段内换行的 CSV 规则。对空文件、无表头、格式不完整或超过现有上传限制的文件返回明确错误。
  Select the delimiter with the most consistent field counts from comma, tab, and semicolon. Follow CSV quoting, escaped double quotes, and embedded-newline rules. Return clear errors for empty files, missing headers, malformed quoting, or files exceeding existing upload limits.
- CSV 按单工作表处理，可作为摘要、合并清洗、对账和自由模式的输入；工作流现有文件数量及字段映射约束不变。
  Treat CSV as a single-sheet input for summary, merge/cleanup, reconciliation, and freeform workflows; existing file-count and field-mapping constraints remain unchanged.
- 摘要和合并清洗工作流支持显式选择 XLSX 或 CSV 输出，默认 XLSX。CSV 输出编码为带 UTF-8 BOM 的 UTF-8，分隔符固定为逗号。
  Summary and merge/cleanup workflows support an explicit XLSX or CSV output choice, defaulting to XLSX. CSV output uses UTF-8 with a BOM and comma delimiter.
- 对账工作流保留 XLSX 输出，因为结果可能包含多个分类工作表；自由模式也保留 XLSX，以免输出选择与 Agent 生成结构不一致。
  Reconciliation remains XLSX because results may contain multiple categorized sheets. Freeform also remains XLSX so the chosen format cannot conflict with Agent-generated structure.

## 数据流与组件 / Data Flow and Components

1. 文件上传控制器按扩展名分派校验：XLSX 走原有 ZIP/OOXML 检查，CSV 走有界读取、编码识别和语法解析；原始文件名、MIME 类型、大小、存储和用户归属仍走现有文件模型。
   The upload controller dispatches validation by extension: XLSX uses existing ZIP/OOXML checks; CSV uses bounded reading, encoding detection, and syntax parsing. Original name, MIME type, size, storage, and ownership continue through the existing file model.
2. Excel 服务提供统一的读取入口：根据已存文件路径/扩展名读取 XLSX 或 CSV，并返回现有工作簿/工作表抽象，使预览与确定性工作流共享同一数据结构。
   The Excel service exposes one read entry point: read XLSX or CSV based on stored path/extension and return the existing workbook/sheet abstraction so previews and deterministic workflows share one data structure.
3. 任务预览继续展示工作表名和表头；CSV 显示一个 `csv_data` 工作表。预览指纹需包含导出格式选项，确认任务时校验该选项，防止预览后更改输出选择。
   Task preview continues to show sheet names and headers; CSV shows one `csv_data` sheet. Include output format in the preview fingerprint and validate it when creating the task so the choice cannot change after preview.
4. 确定性工作流导出工具依据已校验的输出格式调用 CSV 或 XLSX 写入器。CSV 结果继续作为普通结果文件登记，沿用当前用户校验和下载接口。
   The deterministic workflow export tool selects the CSV or XLSX writer according to the validated output format. CSV results are registered as ordinary result files and use the existing ownership checks and download endpoint.

## 约束与错误处理 / Constraints and Error Handling

- 不新增 CSV 第三方依赖；CSV 解析器必须正确处理引号字段和字段内换行，不使用简单按行/逗号切分。
  Do not add a third-party CSV dependency. The parser must correctly handle quoted fields and embedded newlines; simple line/comma splitting is not acceptable.
- 复用现有单文件大小、套餐限制、累计输入大小、文件归属和任务预览确认校验，不因 CSV 扩大资源上限。
  Reuse existing per-file size, plan, aggregate input size, ownership, and task preview/confirmation checks; CSV must not expand resource limits.
- CSV 解码或语法错误应在上传/预览阶段被拒绝，不应等到后台任务失败。错误消息不回显文件内容。
  Reject decoding or syntax errors during upload or preview rather than waiting for a background task failure. Error messages must not echo file contents.
- 仅当工作流为摘要或合并清洗时允许 `outputFormat=CSV`；其余工作流拒绝 CSV 选项或在界面禁用该选项，并由服务端再次校验。
  Allow `outputFormat=CSV` only for summary and merge/cleanup. Other workflows reject the CSV option or disable it in the UI, with server-side validation as the authority.
- 表头唯一性、重复表头处理和空行语义应沿用当前工作流规则；CSV 解析结果不静默丢弃非空字段或列。
  Preserve current workflow behavior for header uniqueness, duplicate headers, and blank rows; CSV parsing must not silently drop nonempty fields or columns.

## 验证策略 / Verification Strategy

- 单元测试覆盖编码/BOM、分隔符、引号逗号、转义引号、字段内换行、空文件、列数不一致和非法引号。
  Unit tests cover encoding/BOM, delimiter detection, quoted commas, escaped quotes, embedded newlines, empty files, inconsistent column counts, and malformed quotes.
- 上传测试覆盖 CSV/XLSX 扩展名分派、无效文件拒绝及原有大小限制。
  Upload tests cover CSV/XLSX extension dispatch, invalid-file rejection, and existing size limits.
- 工作流服务和工具测试覆盖 CSV 单表预览、摘要/合并 CSV 输出、UTF-8 BOM，以及非支持工作流拒绝 CSV 输出。
  Workflow service and tool tests cover CSV single-sheet preview, summary/merge CSV output, UTF-8 BOM, and rejection of CSV output for unsupported workflows.
- 运行后端相关 Maven 测试、后端全套测试和静态前端模块语法检查；按用户要求暂不做手动 UI 验收。
  Run relevant backend Maven tests, the full backend suite, and static frontend module syntax checks; defer manual UI acceptance as requested.

## 非目标 / Out of Scope

- CSV 多工作表、带格式或图表导出、ZIP 打包多份 CSV、Excel 公式执行、用户自定义分隔符和用户选择编码。
  CSV multi-sheet output, formatted/chart exports, ZIP bundles of multiple CSVs, Excel formula execution, user-defined delimiters, and user-selected encodings.
- 云盘连接、定时导入、超出现有大小限制的大文件流式处理。
  Cloud storage connections, scheduled imports, and streaming files beyond existing size limits.
