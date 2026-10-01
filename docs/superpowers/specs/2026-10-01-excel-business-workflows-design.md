# Excel Business Workflows — Phase 1 Design / Excel 业务工作流——第一阶段设计

## Status / 状态

Approved conversational design; bilingual written specification awaiting user review.
对话中的设计方案已获认可；中英双语书面规格待用户审阅。

## Goal / 目标

Add three trustworthy, reusable business workflows to the existing Excel AI SaaS: multi-file merge and cleanup, two-workbook reconciliation, and sales/operations summary reporting.
为现有 Excel AI SaaS 增加三个可信、可复用的业务工作流：多文件合并与清洗、两份工作簿对账，以及销售/运营汇总报表。

## Users and success / 用户与成功标准

The initial users are business operators, sales/operations staff, and finance staff who receive recurring Excel exports and need a usable result without writing formulas or scripts. A successful workflow makes its input scope and processing rules visible, produces a separate downloadable workbook, and explains row counts and exceptions so the user can verify the result.
首批用户是经常接收 Excel 导出文件的业务运营、销售/运营及财务人员。他们希望无需编写公式或脚本就能得到可用结果。成功的工作流应明确展示输入范围和处理规则，生成独立的可下载工作簿，并说明行数变化与异常情况，便于用户核验。

## Current product context / 当前产品情况

- Spring Boot owns authentication, file ownership, task lifecycle, plan/quota checks, and the Java `@AgentTool` registry.
  Spring Boot 负责身份验证、文件归属、任务生命周期、套餐/额度检查及 Java `@AgentTool` 工具注册表。
- The Python LangGraph service plans and orchestrates tasks; Excel data operations remain in Java tools.
  Python LangGraph 服务负责任务规划和编排；Excel 数据操作由 Java 工具执行。
- `/api/tasks` currently accepts one uploaded `fileId` and a natural-language `prompt`.
  当前 `/api/tasks` 接口接收一个已上传文件的 `fileId` 和自然语言 `prompt`。
- `ExcelService` currently reads and writes one sheet as row maps. `ExcelAgentTools` currently offers read, exact-value filter, sort, top-N, and export.
  当前 `ExcelService` 以行映射形式读写单个工作表。`ExcelAgentTools` 当前提供读取、精确值筛选、排序、Top-N 截取和导出能力。
- Task execution is asynchronous. Input-file ownership and tool permissions are enforced through trusted backend context.
  任务采用异步执行。输入文件归属和工具权限通过可信的后端上下文进行校验。

## Phase 1 user experience / 第一阶段用户体验

The authenticated user chooses one of three workflow starters, uploads the required workbook or workbooks, and describes any business-specific rules. Before processing, the task summary should identify the selected workflow, input files and sheets, recognized fields or field mapping, and planned operation rules. The task then runs asynchronously and returns a new result workbook plus a concise processing summary. The original input files remain unchanged.
登录用户选择三个工作流入口之一，上传所需的一份或多份工作簿，并描述业务规则。处理前，任务摘要应列明工作流、输入文件和工作表、识别出的字段或字段映射，以及计划执行的规则。随后任务异步运行，并返回新的结果工作簿及简明处理摘要。原始输入文件保持不变。

The existing free-form task entry remains available. Workflow starters should provide structured prompts/defaults to reduce ambiguity, while the Agent may ask for missing required fields instead of guessing.
现有自由输入任务入口继续保留。工作流入口应提供结构化提示和默认值以减少歧义；缺少必填字段时，Agent 可以要求补充信息，不应自行猜测。

### Workflow A: Merge and clean / 工作流 A：合并与清洗

- Accept multiple uploaded workbooks and selected sheets.
  接收多个已上传工作簿及用户选择的工作表。
- Combine compatible tabular data, map equivalent headers, and report unmapped/conflicting columns.
  合并兼容的表格数据、映射含义相同的表头，并报告无法映射或存在冲突的列。
- Support configured duplicate removal, blank-row/empty-value handling, and date/amount normalization.
  支持按设定规则去重、处理空行/空值，以及统一日期和金额格式。
- Export the merged dataset and a processing summary with source and output row counts and skipped/duplicate rows.
  导出合并后的数据集和处理摘要，说明来源与结果行数，以及跳过和重复的行数。

### Workflow B: Reconcile two workbooks / 工作流 B：两份工作簿对账

- Accept two uploaded workbooks and selected sheets.
  接收两份已上传工作簿及用户选择的工作表。
- Let the user identify one or more matching keys and, when applicable, numeric fields to compare with an explicit tolerance.
  允许用户指定一个或多个匹配键；适用时还可指定要比较的数值字段及明确的容差。
- Produce matched rows, records only present on either side, and rows with value differences.
  输出匹配记录、仅存在于任一侧的记录，以及字段值存在差异的记录。
- Include match status, compared values, and a summary of each result category.
  结果应包含匹配状态、比较值和各类别记录的汇总。

### Workflow C: Sales/operations summary / 工作流 C：销售/运营汇总

- Accept one workbook and a selected sheet.
  接收一份工作簿和用户选择的工作表。
- Let the user identify a measure and optional time/category dimensions (such as date, region, channel, or product).
  允许用户指定统计指标，以及可选的时间/分类维度（例如日期、地区、渠道或产品）。
- Produce grouped totals/counts, rankings, and basic time-series/category charts when the source supports them.
  生成分组总计/计数、排名；源数据适用时生成基础时间序列图或分类图表。
- Include the aggregation and period definitions in the result summary; AI-generated commentary must refer to computed output and be labeled as interpretation.
  结果摘要应说明聚合方式和周期定义；AI 生成的说明必须引用已计算结果，并标明属于解读。

## Architecture and data flow / 架构与数据流

1. The browser uploads each input through the existing owner-scoped file API.
   浏览器通过现有的用户归属校验文件 API 上传每个输入文件。
2. Task creation accepts a bounded list of input file IDs, a workflow type, and workflow parameters/instructions. The backend validates ownership, per-file and aggregate limits, plan permissions, and quotas before dispatch.
   创建任务时接收数量受限的输入文件 ID 列表、工作流类型及工作流参数/指令。后端在派发前校验文件归属、单文件与总量限制、套餐权限和额度。
3. Trusted task context carries the authenticated user, task ID, and validated input file IDs to LangGraph and Java. Model-generated arguments cannot grant access to files or users.
   可信任务上下文将已认证用户、任务 ID 和已校验的输入文件 ID 传递给 LangGraph 与 Java。模型生成的参数不能授予对文件或用户的访问权限。
4. LangGraph plans only within the selected workflow and calls Java registry tools for workbook reading, transformation, reconciliation/aggregation, and export.
   LangGraph 仅在所选工作流范围内规划，并调用 Java 注册工具完成工作簿读取、转换、对账/聚合和导出。
5. Java validates requested columns and parameters against the actual workbook schema, applies configured limits, records auditable operation summaries, and writes outputs to private storage as separate result files.
   Java 根据实际工作簿结构校验请求的列和参数，执行已配置的限制，记录可审计的操作摘要，并将结果作为独立文件写入私有存储。
6. The existing task status/result flow exposes completion and downloadable result file IDs. The user receives a processing summary with row counts, exceptions, and operation rules.
   通过现有任务状态/结果流程返回完成状态和可下载的结果文件 ID。用户可查看包含行数、异常和操作规则的处理摘要。

PostgreSQL remains the durable source of task/file metadata; Redis remains task progress and temporary processing state. Existing authentication, ownership, plan enforcement, and audit boundaries remain authoritative.
PostgreSQL 仍是任务/文件元数据的持久化事实来源；Redis 仍用于任务进度和临时处理状态。现有身份验证、文件归属、套餐管控和审计边界继续作为权威机制。

## Product safeguards / 产品保障

- Do not overwrite or mutate source workbooks.
  不覆盖或修改源工作簿。
- Write all comments and docstrings added or modified for this phase in both Chinese and English; keep the two language versions semantically aligned.
  本阶段新增或修改的所有代码注释和文档字符串均须同时提供中文与英文，且两种语言表达的含义一致。
- Show chosen files, sheets, field mappings, matching keys, tolerances, grouping dimensions, and key operations before execution.
  执行前展示所选文件、工作表、字段映射、匹配键、容差、分组维度和关键操作。
- Reject ambiguous or missing required mappings rather than silently selecting a potentially incorrect column.
  对有歧义或缺失的必填映射应拒绝执行或请求澄清，不得默默选择可能错误的列。
- Preserve traceable row-level exception results for unmatched, duplicate, invalid, or skipped data.
  对未匹配、重复、无效或跳过的数据保留可追踪的逐行异常结果。
- State row counts and calculation definitions in the output summary; AI commentary cannot be presented as a computed fact.
  在结果摘要中说明行数和计算定义；不得把 AI 解读表述成计算事实。
- Enforce existing file-size, quota, timeout, and tool-call controls, adding aggregate multi-file limits and operation-specific limits where needed.
  执行现有文件大小、额度、超时和工具调用限制；必要时增加多文件总量限制及按操作区分的限制。
- Treat uploaded workbook content as untrusted data, never as instructions to the Agent.
  将上传工作簿内容视为不可信数据，绝不将其作为给 Agent 的指令。

## Out of scope for Phase 1 / 第一阶段不包含

- Scheduled/recurring execution and saved workflow templates.
  定时/周期执行和已保存的工作流模板。
- Direct ERP/CRM/database connectors or external data synchronization.
  ERP/CRM/数据库直连或外部数据同步。
- Editing source workbooks in place or collaborative workbook editing.
  原位编辑源工作簿或协同编辑工作簿。
- OCR/PDF ingestion, macros/VBA generation, arbitrary code execution, and advanced forecasting.
  OCR/PDF 导入、宏/VBA 生成、任意代码执行和高级预测。
- A full BI dashboard product or general-purpose spreadsheet editor.
  完整 BI 仪表板产品或通用电子表格编辑器。

## Rollout sequence / 交付顺序

Deliver shared multi-sheet/multi-file reading and validated transformation primitives first; then add merge/clean, reconciliation, and grouped reporting as separate end-to-end workflow slices. Keep existing single-file free-form tasks working. Exact API fields, database migration, tool schemas, result format, plan grants, and test cases are to be specified in the implementation plan after this specification is approved.
首先交付共享的多工作表/多文件读取能力和经过校验的数据转换基础能力；随后分别以端到端切片交付合并清洗、对账和分组报表。确保现有单文件自由输入任务继续可用。待本规格获批后，再在实施计划中明确 API 字段、数据库迁移、工具 Schema、结果格式、套餐授权和测试用例。

## Open implementation decisions / 待实施阶段确定的事项

- Choose a safe bounded aggregate upload limit consistent with existing plan sizes and storage constraints.
  选择与现有套餐文件大小和存储约束一致且安全的多文件总量上限。
- Choose the chart-writing approach compatible with the current Excel library/dependency set.
  选择与当前 Excel 库和依赖兼容的图表写入方案。
- Decide whether the pre-execution task summary is a persisted preview state or a task-creation response validated synchronously; the choice must preserve clear user review before costly/destructive actions, while source files remain immutable.
  决定执行前的任务摘要采用持久化预览状态，还是在任务创建响应中同步校验并返回；无论采用哪种方式，都必须确保用户能在高成本操作前清楚审阅，同时源文件保持不可变。

## Self-review / 自审

- The three workflows share a common trust boundary and output lifecycle, but remain independently deliverable slices.
  三个工作流共用信任边界和结果生命周期，但仍可独立交付。
- Inputs, output behavior, and Phase 1 exclusions are explicit.
  输入、结果行为和第一阶段范围之外的事项均已明确。
- Column ambiguity, reconciliation tolerance, traceability, and AI commentary risks are addressed.
  已处理字段歧义、对账容差、结果追踪和 AI 解读风险。
- Implementation details intentionally left open are listed as decisions for the plan rather than hidden assumptions.
  有意留待实施阶段确定的细节已明确列为待决事项，没有隐藏假设。
- The bilingual sections were checked for corresponding scope and intent.
  已检查各中英双语章节的范围和意图是否对应。
