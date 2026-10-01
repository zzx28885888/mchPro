# Excel Business Workflows — Phase 1 Implementation Plan / Excel 业务工作流——第一阶段实施计划

> **For agentic workers / 面向代理式实施者：** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
> **实施子技能要求：** 按任务逐项执行时，必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans`。步骤使用复选框语法跟踪。

**Goal:** Deliver merge/clean, two-workbook reconciliation, and sales/operations summary workflows in the existing Excel AI SaaS while preserving source files and making results auditable.
**目标：** 在现有 Excel AI SaaS 中交付合并清洗、两份工作簿对账和销售/运营汇总工作流，同时保留源文件并确保结果可审计。

**Architecture:** Extend the existing authenticated asynchronous task flow to accept a bounded set of owner-scoped inputs and a typed workflow request. Keep workbook access and deterministic transformations in Java registry tools; use LangGraph to interpret user intent and orchestrate only the selected workflow; return a separate workbook and a structured processing summary.
**架构：** 扩展现有已认证的异步任务流程，使其接收数量受限且归属当前用户的输入文件集合，以及带类型的工作流请求。工作簿读取和确定性转换继续由 Java 注册工具执行；LangGraph 负责理解用户意图，并仅编排已选择的工作流；最终返回独立工作簿和结构化处理摘要。

**Tech Stack:** Java 21, Spring Boot 4, MyBatis-Plus, PostgreSQL, Redis, EasyExcel (and Apache POI only if needed for charts), Python 3 with FastAPI/LangGraph, existing static HTML/JavaScript UI.
**技术栈：** Java 21、Spring Boot 4、MyBatis-Plus、PostgreSQL、Redis、EasyExcel（仅在图表需要时使用 Apache POI）、Python 3、FastAPI/LangGraph，以及现有静态 HTML/JavaScript 页面。

**Spec:** [`docs/superpowers/specs/2026-10-01-excel-business-workflows-design.md`](../specs/2026-10-01-excel-business-workflows-design.md)
**规格：** [`docs/superpowers/specs/2026-10-01-excel-business-workflows-design.md`](../specs/2026-10-01-excel-business-workflows-design.md)

## Global Constraints / 全局约束

- “Do not overwrite or mutate source workbooks.” / “不覆盖或修改源工作簿。”
- “Write all comments and docstrings added or modified for this phase in both Chinese and English; keep the two language versions semantically aligned.” / “本阶段新增或修改的所有代码注释和文档字符串均须同时提供中文与英文，且两种语言表达的含义一致。”
- “Reject ambiguous or missing required mappings rather than silently selecting a potentially incorrect column.” / “对有歧义或缺失的必填映射应拒绝执行或请求澄清，不得默默选择可能错误的列。”
- “Preserve traceable row-level exception results for unmatched, duplicate, invalid, or skipped data.” / “对未匹配、重复、无效或跳过的数据保留可追踪的逐行异常结果。”
- “Treat uploaded workbook content as untrusted data, never as instructions to the Agent.” / “将上传工作簿内容视为不可信数据，绝不将其作为给 Agent 的指令。”
- Preserve the existing Java tool registry as the only business-operation boundary; never accept user IDs or storage paths from model-generated tool arguments. / 保留现有 Java 工具注册表作为唯一业务操作边界；绝不接受模型生成参数提供的用户 ID 或存储路径。
- Keep existing single-file free-form tasks and existing ownership, quotas, timeouts, and audit checks working. / 保持现有单文件自由输入任务，以及文件归属、额度、超时和审计校验继续有效。

## Review Focus / 审查重点

1. A request mixes another user's file ID with owned files; reject the whole request without creating a task. Pin in Task 2 with `TaskServiceTest.createRejectsAnyUnownedInput`. / 请求将他人文件 ID 与本人文件混用；必须整体拒绝且不创建任务。在任务 2 中用 `TaskServiceTest.createRejectsAnyUnownedInput` 固定此行为。
2. Two sheets contain duplicate, blank, or conflicting headers; require explicit mapping or report the conflict without silently merging columns. Pin in Task 1 with `ExcelServiceTest.readsAllSheetsAndPreservesHeaderConflicts`. / 两个工作表存在重复、空白或冲突表头；必须要求明确映射或报告冲突，不得静默合并列。在任务 1 中用 `ExcelServiceTest.readsAllSheetsAndPreservesHeaderConflicts` 固定此行为。
3. Reconciliation keys are blank or duplicated on either side; classify ambiguity and preserve the source rows rather than arbitrarily pairing them. Pin in Task 4 with `ExcelWorkflowServiceTest.reconciliationReportsBlankAndDuplicateKeys`. / 对账键在任一侧为空或重复；应标记歧义并保留源行，不得任意配对。在任务 4 中用 `ExcelWorkflowServiceTest.reconciliationReportsBlankAndDuplicateKeys` 固定此行为。
4. Numeric values use mixed decimal separators, currencies, or tolerance boundaries; reject values that cannot be normalized and apply the documented inclusive tolerance. Pin in Task 4 with `ExcelWorkflowServiceTest.reconciliationAppliesInclusiveToleranceAndReportsInvalidNumbers`. / 数值混用小数分隔符、币种或处于容差边界；无法标准化的值应列为异常，已定义容差按含边界规则应用。在任务 4 中用 `ExcelWorkflowServiceTest.reconciliationAppliesInclusiveToleranceAndReportsInvalidNumbers` 固定此行为。
5. Dates are missing, invalid, or span multiple periods; exclude invalid dates from time grouping but report them, and label the chosen period definition. Pin in Task 5 with `ExcelWorkflowServiceTest.summaryReportsInvalidDatesAndPeriodDefinition`. / 日期缺失、无效或跨多个周期；时间分组应排除无效日期但在异常中报告，并标明周期定义。在任务 5 中用 `ExcelWorkflowServiceTest.summaryReportsInvalidDatesAndPeriodDefinition` 固定此行为。

---

## File Map / 文件职责

- Modify `infra/init.sql` and create `infra/migrations/<version>_excel_workflows.sql`: durable multi-input task links, workflow type, and structured result summary. / 修改 `infra/init.sql` 并创建 `infra/migrations/<version>_excel_workflows.sql`：保存任务的多输入关联、工作流类型和结构化结果摘要。
- Modify `backend/src/main/java/com/excelai/task/*`: validate and persist task inputs; dispatch trusted workflow context; return summary/status. / 修改 `backend/src/main/java/com/excelai/task/*`：校验并持久化任务输入、派发可信工作流上下文、返回摘要/状态。
- Modify `backend/src/main/java/com/excelai/agent/AgentService.java` and `backend/src/main/java/com/example/agent/tool/controller/AgentToolController.java`: forward validated input IDs and recover them only from persisted task context. / 修改 `backend/src/main/java/com/excelai/agent/AgentService.java` 和 `backend/src/main/java/com/example/agent/tool/controller/AgentToolController.java`：传递已校验输入 ID，并且只从持久化任务上下文还原这些 ID。
- Extend `backend/src/main/java/com/excelai/excel/ExcelService.java`; add focused `ExcelWorkbook`, `ExcelWorkflowService`, and `ExcelWorkflowTools` classes under `backend/src/main/java/com/excelai/excel/`. / 扩展 `backend/src/main/java/com/excelai/excel/ExcelService.java`；在 `backend/src/main/java/com/excelai/excel/` 下新增职责清晰的 `ExcelWorkbook`、`ExcelWorkflowService` 和 `ExcelWorkflowTools` 类。
- Modify `backend/src/main/resources/static/index.html`: three workflow starters, conditional upload controls, mapping/options form, pre-run review, and result summary. / 修改 `backend/src/main/resources/static/index.html`：增加三个工作流入口、按场景显示上传控件、字段映射/选项表单、执行前确认和结果摘要。
- Modify `agent-service/agent_graph.py`, `agent-service/java_langgraph_tools.py`, and `agent-service/app.py` only as needed to forward typed workflow context and bind matching Java tool schemas. / 仅在需要时修改 `agent-service/agent_graph.py`、`agent-service/java_langgraph_tools.py` 和 `agent-service/app.py`，以传递带类型的工作流上下文并绑定对应 Java 工具 Schema。
- Add focused Java tests under `backend/src/test/java/com/excelai/{excel,task}/` and Python tool-schema tests under `agent-service/tests/`. / 在 `backend/src/test/java/com/excelai/{excel,task}/` 下增加 Java 测试，在 `agent-service/tests/` 下增加 Python 工具 Schema 测试。

## Task 1: Multi-sheet workbook model and bounded readers / 任务 1：多工作表模型与受限读取

**Interfaces / 接口：** `ExcelService.readWorkbook(Path) -> ExcelWorkbook`; `ExcelWorkbook` contains ordered sheet names and rows per sheet, with headers retained as encountered. Preserve `read(Path)` as a compatibility wrapper over the first sheet until existing free-form tools are migrated. / `ExcelService.readWorkbook(Path) -> ExcelWorkbook`；`ExcelWorkbook` 保存有序工作表名称及每个工作表的行数据，并保留原始表头。迁移现有自由输入工具前，`read(Path)` 保留为读取首个工作表的兼容封装。

**Files / 文件：** Modify `backend/src/main/java/com/excelai/excel/ExcelService.java`; create `ExcelWorkbook.java`; test `backend/src/test/java/com/excelai/excel/ExcelServiceTest.java`. / **文件：** 修改 `backend/src/main/java/com/excelai/excel/ExcelService.java`；创建 `ExcelWorkbook.java`；测试文件为 `backend/src/test/java/com/excelai/excel/ExcelServiceTest.java`。

- [ ] Add `ExcelServiceTest.readWorkbookReturnsEverySheetInOrder` and `ExcelServiceTest.readsAllSheetsAndPreservesHeaderConflicts`; assert sheet names/order, row values, blank headers, and duplicate headers remain detectable. / 新增 `ExcelServiceTest.readWorkbookReturnsEverySheetInOrder` 和 `ExcelServiceTest.readsAllSheetsAndPreservesHeaderConflicts`；断言工作表名称/顺序、行值、空表头和重复表头均可被检测。
- [ ] Run `mvn -q -f backend/pom.xml -Dtest=ExcelServiceTest test`; confirm the tests fail against the current first-sheet-only reader. / 运行 `mvn -q -f backend/pom.xml -Dtest=ExcelServiceTest test`；确认当前仅读首表的实现无法通过测试。
- [ ] Implement `ExcelWorkbook` and `ExcelService.readWorkbook(Path)` using the existing EasyExcel dependency; do not collapse duplicate headers into one map key. / 使用现有 EasyExcel 依赖实现 `ExcelWorkbook` 和 `ExcelService.readWorkbook(Path)`；不得将重复表头折叠成同一个 Map 键。
- [ ] Keep `ExcelService.read(Path)` returning the first sheet and run the focused test command; expect PASS. / 保持 `ExcelService.read(Path)` 返回首个工作表，并运行指定测试命令；预期通过。
- [ ] Commit as `feat: read bounded multi-sheet workbooks`. / 提交为 `feat: read bounded multi-sheet workbooks`。

## Task 2: Typed tasks, multi-file ownership, and pre-run review API / 任务 2：带类型任务、多文件归属校验与执行前预览 API

**Interfaces / 接口：** `TaskController.PreviewReq(List<Long> fileIds, WorkflowType workflowType, Map<String,Object> options, String prompt)`; `POST /api/tasks/preview` returns validated file names/sheets, inferred fields, and normalized operation summary. `TaskController.Req` accepts `fileIds`, `workflowType`, `options`, `prompt`, and the preview fingerprint. Add `ai_task_input(task_id,file_id,ordinal)`; preserve `ai_task.input_file_id` as the first-input compatibility field. / **接口：** `TaskController.PreviewReq(List<Long> fileIds, WorkflowType workflowType, Map<String,Object> options, String prompt)`；`POST /api/tasks/preview` 返回已校验的文件名/工作表、推断字段和规范化操作摘要。`TaskController.Req` 接收 `fileIds`、`workflowType`、`options`、`prompt` 和预览指纹。新增 `ai_task_input(task_id,file_id,ordinal)`；保留 `ai_task.input_file_id` 作为首个输入的兼容字段。

**Files / 文件：** Modify `infra/init.sql`, `backend/src/main/java/com/excelai/task/{TaskController,TaskService,TaskRepository,TaskWorker,TaskRedisService}.java`, `backend/src/main/java/com/excelai/persistence/entity/AiTaskEntity.java`, `backend/src/main/java/com/excelai/agent/AgentService.java`, and `backend/src/main/java/com/example/agent/tool/controller/AgentToolController.java`; add an idempotent migration under `infra/migrations/`; test `backend/src/test/java/com/excelai/task/TaskServiceTest.java` and controller tests. / **文件：** 修改 `infra/init.sql`、`backend/src/main/java/com/excelai/task/{TaskController,TaskService,TaskRepository,TaskWorker,TaskRedisService}.java`、`backend/src/main/java/com/excelai/persistence/entity/AiTaskEntity.java`、`backend/src/main/java/com/excelai/agent/AgentService.java` 和 `backend/src/main/java/com/example/agent/tool/controller/AgentToolController.java`；在 `infra/migrations/` 下新增幂等迁移；测试 `backend/src/test/java/com/excelai/task/TaskServiceTest.java` 和 Controller 测试。

- [ ] Add tests for mixed ownership, duplicate IDs, empty lists, invalid workflow cardinality (merge >= 2, reconcile = 2, summary = 1), per-file/aggregate size limits, legacy one-file requests, and preview fingerprint mismatch. / 新增测试覆盖混合文件归属、重复 ID、空列表、工作流文件数量不符（合并 >= 2、对账 = 2、汇总 = 1）、单文件/总大小限制、旧版单文件请求和预览指纹不匹配。
- [ ] Run focused TaskService/controller tests and confirm they fail before implementation. / 运行指定 TaskService/Controller 测试，确认实现前测试失败。
- [ ] Add the join table and workflow metadata/summary persistence; make task input ordering deterministic. / 新增任务输入关联表及工作流元数据/摘要持久化；确保输入文件顺序稳定。
- [ ] Implement preview by checking every file with `FileRepository.findOwned`, reading workbook schemas, validating cardinality/options, and returning a short-lived fingerprint bound to user, ordered file IDs, workflow, and normalized options. Reject preview/task mismatch. / 通过 `FileRepository.findOwned` 校验每个文件，读取工作簿结构，验证文件数量/选项，并生成与用户、排序后的文件 ID、工作流及规范化选项绑定的短期指纹。拒绝预览与建任务参数不一致的请求。
- [ ] Make `TaskService.create` revalidate ownership, plan size/quota and fingerprint, persist all inputs, and pass trusted context `inputFileIds`, `workflowType`, `options`, and `maxToolCalls`; model tool arguments cannot override context. / 使 `TaskService.create` 重新校验文件归属、套餐大小/额度和指纹，持久化全部输入，并传递可信上下文 `inputFileIds`、`workflowType`、`options` 和 `maxToolCalls`；模型工具参数不得覆盖上下文。
- [ ] Keep the existing `{fileId,prompt}` request working by normalizing it to one-file `FREEFORM`; run focused tests and expect PASS. / 将现有 `{fileId,prompt}` 请求规范化为单文件 `FREEFORM` 并保持兼容；运行指定测试并确认通过。
- [ ] Commit as `feat: support validated multi-file workflow tasks`. / 提交为 `feat: support validated multi-file workflow tasks`。

## Task 3: Shared deterministic merge and cleanup / 任务 3：共享的确定性合并与清洗

**Interfaces / 接口：** `ExcelWorkflowService.mergeAndClean(List<SheetInput>, MergeOptions) -> WorkflowResult`; each `SheetInput` includes trusted file ID, sheet name, source rows and original headers. `WorkflowResult` includes output sheets, row counts, warnings, and row-level exceptions. / **接口：** `ExcelWorkflowService.mergeAndClean(List<SheetInput>, MergeOptions) -> WorkflowResult`；每个 `SheetInput` 包含可信文件 ID、工作表名称、源行和原始表头。`WorkflowResult` 包含输出工作表、行数、警告和逐行异常。

**Files / 文件：** Create/modify `backend/src/main/java/com/excelai/excel/{ExcelWorkflowService,WorkflowResult,SheetInput}.java`; test `backend/src/test/java/com/excelai/excel/ExcelWorkflowServiceTest.java`. / **文件：** 创建/修改 `backend/src/main/java/com/excelai/excel/{ExcelWorkflowService,WorkflowResult,SheetInput}.java`；测试文件为 `backend/src/test/java/com/excelai/excel/ExcelWorkflowServiceTest.java`。

- [ ] Add tests `mergeMapsExplicitHeaderAliases`, `mergeReportsConflictingAndUnmappedHeaders`, `cleanupRemovesConfiguredDuplicatesAndBlankRows`, and `cleanupReportsInvalidDateOrAmount`; assert source rows are unchanged. / 新增测试 `mergeMapsExplicitHeaderAliases`、`mergeReportsConflictingAndUnmappedHeaders`、`cleanupRemovesConfiguredDuplicatesAndBlankRows` 和 `cleanupReportsInvalidDateOrAmount`；断言源行不被修改。
- [ ] Run the focused workflow tests and confirm failure. / 运行指定工作流测试并确认失败。
- [ ] Implement deterministic header mapping, duplicate policy, blank-row handling, and explicitly configured date/amount normalization; ambiguous mappings become exceptions, never inferred merges. / 实现确定性的表头映射、去重策略、空行处理及显式配置的日期/金额标准化；映射歧义应作为异常返回，禁止推断式合并。
- [ ] Run focused tests and confirm PASS, including row-level source provenance in exceptions. / 运行指定测试并确认通过，异常结果中须包含逐行来源信息。
- [ ] Commit as `feat: merge and clean Excel workbooks`. / 提交为 `feat: merge and clean Excel workbooks`。

## Task 4: Reconcile two workbooks / 任务 4：两份工作簿对账

**Interfaces / 接口：** `ExcelWorkflowService.reconcile(SheetInput left, SheetInput right, ReconcileOptions) -> WorkflowResult`; output categories are `MATCHED`, `LEFT_ONLY`, `RIGHT_ONLY`, `VALUE_DIFFERENCE`, and `AMBIGUOUS_KEY`. Numeric tolerance is inclusive (`abs(left-right) <= tolerance`). / **接口：** `ExcelWorkflowService.reconcile(SheetInput left, SheetInput right, ReconcileOptions) -> WorkflowResult`；结果类别为 `MATCHED`、`LEFT_ONLY`、`RIGHT_ONLY`、`VALUE_DIFFERENCE` 和 `AMBIGUOUS_KEY`。数值容差含边界（`abs(left-right) <= tolerance`）。

**Files / 文件：** Modify `ExcelWorkflowService.java`, add `ReconcileOptions.java`; test `backend/src/test/java/com/excelai/excel/ExcelWorkflowServiceTest.java`. / **文件：** 修改 `ExcelWorkflowService.java`，新增 `ReconcileOptions.java`；测试 `backend/src/test/java/com/excelai/excel/ExcelWorkflowServiceTest.java`。

- [ ] Add tests `reconciliationReportsBlankAndDuplicateKeys`, `reconciliationAppliesInclusiveToleranceAndReportsInvalidNumbers`, and `reconciliationPreservesUnmatchedRows`; assert no arbitrary pairing occurs for ambiguous keys. / 新增测试 `reconciliationReportsBlankAndDuplicateKeys`、`reconciliationAppliesInclusiveToleranceAndReportsInvalidNumbers` 和 `reconciliationPreservesUnmatchedRows`；断言歧义键不会被任意配对。
- [ ] Run focused tests and confirm failure. / 运行指定测试并确认失败。
- [ ] Implement deterministic key normalization, one-to-one matches only for unique nonblank keys, categorized outputs, and explicit tolerance comparisons. / 实现确定性键标准化；仅对唯一且非空的键执行一对一匹配；生成分类结果并按明确容差比较。
- [ ] Run focused tests and confirm PASS; ensure numeric parse failures are included as row exceptions. / 运行指定测试并确认通过；数值解析失败须纳入逐行异常。
- [ ] Commit as `feat: reconcile Excel workbooks`. / 提交为 `feat: reconcile Excel workbooks`。

## Task 5: Grouped reporting and chart workbook output / 任务 5：分组报表与图表工作簿输出

**Interfaces / 接口：** `ExcelWorkflowService.summarize(SheetInput input, SummaryOptions) -> WorkflowResult`; `ExcelService.writeWorkbook(Path, List<OutputSheet>)`. Summary options name measure, dimensions, aggregation (`SUM` or `COUNT`), and optional date period (`DAY`, `MONTH`, `QUARTER`, `YEAR`). / **接口：** `ExcelWorkflowService.summarize(SheetInput input, SummaryOptions) -> WorkflowResult`；`ExcelService.writeWorkbook(Path, List<OutputSheet>)`。汇总选项指定统计指标、维度、聚合方式（`SUM` 或 `COUNT`）及可选日期周期（`DAY`、`MONTH`、`QUARTER`、`YEAR`）。

**Files / 文件：** Modify `ExcelService.java`, `ExcelWorkflowService.java`; create `SummaryOptions.java`, `OutputSheet.java`; add direct Apache POI OOXML dependency only if chart generation cannot be implemented through the currently resolved dependencies; test `ExcelWorkflowServiceTest.java` and `ExcelServiceTest.java`. / **文件：** 修改 `ExcelService.java`、`ExcelWorkflowService.java`；新增 `SummaryOptions.java`、`OutputSheet.java`；仅当当前解析到的依赖无法实现图表时，才添加 Apache POI OOXML 直接依赖；测试 `ExcelWorkflowServiceTest.java` 和 `ExcelServiceTest.java`。

- [ ] Add tests `summaryGroupsAndRanksByConfiguredDimensions`, `summaryReportsInvalidDatesAndPeriodDefinition`, `summaryRejectsUnknownMeasureOrDimension`, and `writeWorkbookCreatesNamedSheetsAndChartsWhenSupported`. / 新增测试 `summaryGroupsAndRanksByConfiguredDimensions`、`summaryReportsInvalidDatesAndPeriodDefinition`、`summaryRejectsUnknownMeasureOrDimension` 和 `writeWorkbookCreatesNamedSheetsAndChartsWhenSupported`。
- [ ] Run focused tests and confirm failure. / 运行指定测试并确认失败。
- [ ] Implement stable grouped aggregation/ranking, period labels, invalid-date exceptions, and summary sheets. Add a basic chart only for chartable grouped/time-series output; keep workbook usable if chart creation is unsupported. / 实现稳定的分组聚合/排名、周期标签、无效日期异常和摘要工作表。仅对适合绘图的分组/时间序列结果添加基础图表；若无法生成图表，工作簿数据仍须可用。
- [ ] Run focused tests and confirm PASS; verify output sheet names and chart series reference the computed table. / 运行指定测试并确认通过；校验输出工作表名称以及图表数据系列引用已计算的数据表。
- [ ] Commit as `feat: create grouped Excel summary reports`. / 提交为 `feat: create grouped Excel summary reports`。

## Task 6: Workflow tools, permissions, and result summary / 任务 6：工作流工具、权限和结果摘要

**Interfaces / 接口：** Register `inspect_workflow_inputs`, `merge_clean_workbooks`, `reconcile_workbooks`, `summarize_workbook`, and `export_workbook_result` Java tools. Input file IDs and workflow type come from `AgentToolContext`; tools may accept sheet/column/options only, never user IDs or paths. Persist a JSON result summary with counts, exceptions, and operation definitions; expose it in task GET. / **接口：** 注册 Java 工具 `inspect_workflow_inputs`、`merge_clean_workbooks`、`reconcile_workbooks`、`summarize_workbook` 和 `export_workbook_result`。输入文件 ID 与工作流类型来自 `AgentToolContext`；工具只可接收工作表/列/选项，不得接收用户 ID 或路径。持久化包含数量、异常和操作定义的 JSON 结果摘要，并通过任务 GET 接口返回。

**Files / 文件：** Modify `ExcelAgentTools.java`, `TaskDataService.java`, `TaskRepository.java`, `TaskController.java`, `AgentService.java`, `infra/init.sql`, and plan permission seeds; add `ExcelWorkflowTools.java`; modify `agent-service/java_langgraph_tools.py` and `agent-service/agent_graph.py`; test Java registry/context/controller and Python schemas. / **文件：** 修改 `ExcelAgentTools.java`、`TaskDataService.java`、`TaskRepository.java`、`TaskController.java`、`AgentService.java`、`infra/init.sql` 和套餐工具权限初始化数据；新增 `ExcelWorkflowTools.java`；修改 `agent-service/java_langgraph_tools.py` 与 `agent-service/agent_graph.py`；测试 Java 注册表/上下文/Controller 及 Python Schema。

- [ ] Add tests asserting model-supplied file/user IDs are rejected, ungranted workflow tools are denied, task context input IDs are ownership-checked, summaries persist/return, and all Python schemas match Java tool names/arguments. / 新增测试断言：拒绝模型提供的文件/用户 ID、拒绝未授权工作流工具、任务上下文输入 ID 经归属校验、摘要可持久化/返回、Python Schema 与 Java 工具名称/参数一致。
- [ ] Run focused Java and Python tests and confirm failure. / 运行指定 Java 和 Python 测试并确认失败。
- [ ] Implement the tool boundary and task summary persistence; retain existing plan and audit enforcement for every tool call. / 实现工具边界与任务摘要持久化；每次工具调用均保留现有套餐授权和审计校验。
- [ ] Update the English system prompt and bilingual comments/docstrings so the Agent follows the selected workflow, inspects inputs first, asks rather than guesses, and exports only after deterministic tools succeed. / 更新英文系统提示及中英双语注释/文档字符串，使 Agent 遵循已选工作流、先检查输入、遇到不确定时先询问，并仅在确定性工具成功后导出。
- [ ] Run focused Java and Python tests and confirm PASS. / 运行指定 Java 和 Python 测试并确认通过。
- [ ] Commit as `feat: orchestrate controlled Excel workflows`. / 提交为 `feat: orchestrate controlled Excel workflows`。

## Task 7: Workflow UI and end-to-end integration / 任务 7：工作流界面与端到端集成

**Interfaces / 接口：** UI preview posts file IDs, workflow type, options and prompt; displays returned normalized preview; confirmed task posts the same values plus fingerprint; polling displays progress, result summary, exceptions, and download action. Existing free-form flow continues using `fileId`/`prompt`. / **接口：** 界面预览请求提交文件 ID、工作流类型、选项和提示词；展示返回的规范化预览；用户确认后以相同参数和指纹创建任务；轮询展示进度、结果摘要、异常及下载入口。现有自由输入流程继续使用 `fileId`/`prompt`。

**Files / 文件：** Modify `backend/src/main/resources/static/index.html`, `docs/learning-guide.md`, and `docs/excel-saas-integration.md`; add/extend API documentation in `docs/`. / **文件：** 修改 `backend/src/main/resources/static/index.html`、`docs/learning-guide.md` 和 `docs/excel-saas-integration.md`；在 `docs/` 下新增或扩展 API 文档。

- [ ] Add UI checks for each workflow's file cardinality, preview-before-run, required column mappings, preview mismatch errors, and summary/exception rendering; retain the legacy free-form upload flow. / 增加界面检查，覆盖各工作流文件数量、先预览再执行、必填字段映射、预览参数不一致错误、摘要/异常展示；保留旧版自由输入上传流程。
- [ ] Implement three workflow starters and dynamic forms; upload inputs, call preview, show file/sheet/field/rule summary, require explicit confirmation, submit the preview fingerprint, and render returned summary/exceptions. / 实现三个工作流入口和动态表单；上传输入文件、调用预览、展示文件/工作表/字段/规则摘要、要求用户明确确认、提交预览指纹，并展示返回摘要/异常。
- [ ] Document request/response examples, migration application, tool permissions, and bilingual comment policy. / 以中英双语记录请求/响应示例、迁移应用方式、工具权限和双语注释要求。
- [ ] Run the full verification set required by the implementation workflow; expected outcomes are backend tests/build, agent-service schema tests, migration initialization, and manual UI flows for all three workflows plus the legacy flow. / 按实施流程运行完整验证；预期结果包括后端测试/构建通过、Agent 服务 Schema 测试通过、迁移初始化成功，以及三个新工作流和旧版流程的手动界面检查通过。
- [ ] Commit as `feat: expose Excel business workflows in UI`. / 提交为 `feat: expose Excel business workflows in UI`。

## Self-Review / 自审

- Spec coverage: all three workflows, multi-sheet/multi-file inputs, pre-run review, immutable source files, traceable exceptions, task summaries, trust boundaries, limits, and exclusions are mapped to Tasks 1–7. / 规格覆盖：三个工作流、多工作表/多文件输入、执行前预览、源文件不可变、异常可追踪、任务摘要、信任边界、限制和范围之外事项均已映射到任务 1–7。
- Type consistency: the workflow request carries ordered `fileIds`, `workflowType`, `options`, `prompt`, and preview fingerprint from UI through task persistence/context; deterministic services consume typed input/options and return `WorkflowResult`. / 类型一致性：工作流请求中的有序 `fileIds`、`workflowType`、`options`、`prompt` 和预览指纹从界面贯穿任务持久化/上下文；确定性服务接收带类型输入/选项并返回 `WorkflowResult`。
- Review Focus failures each map to a named test and owning task. / 每项审查重点及失败情形均映射到明确测试和负责任务。
- Existing free-form compatibility is included in Tasks 1, 2, and 7; authorization and auditing remain in Tasks 2 and 6. / 任务 1、2、7 覆盖现有自由输入兼容；任务 2、6 保留授权和审计。
- All plan prose, headings, UI text requirements, and code-comment policy are bilingual; code comments/docstrings added during implementation must be Chinese/English aligned. / 计划正文、标题、界面文案要求和代码注释规则均为中英双语；实施阶段新增的代码注释/文档字符串必须中英文含义一致。

---

Plan complete and saved to `docs/superpowers/plans/2026-10-01-excel-business-workflows.md`. Please review the plan. Which execution approach would you prefer?
计划已完成并保存至 `docs/superpowers/plans/2026-10-01-excel-business-workflows.md`。请审阅计划。你希望采用哪种执行方式？

- **Subagent-driven / 子代理驱动：** A fresh subagent implements each task and a fresh reviewer checks it before the next task starts, followed by a whole-branch review. Recommended for this plan because it has seven cross-layer slices and an error in task persistence or tool authorization could compromise all workflows. / 每项任务由新的子代理实现，并由新的审阅者检查后再进入下一任务，最后进行整体分支审查。此计划涉及七个跨层切片，且任务持久化或工具授权错误会影响所有工作流，因此推荐此方式。
- **Native / 当前会话执行：** I implement all tasks in this session, then one fresh reviewer checks the completed branch. / 由我在当前会话中完成所有任务，再由一名新的审阅者检查整个分支。

Does the plan capture what you want, and which approach should we use?
计划是否准确表达了你的目标？你希望采用哪种执行方式？
