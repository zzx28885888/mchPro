# 项目进度与功能路线图 / Project Status and Feature Roadmap

**更新日期：** 2026-10-19
**Updated:** 2026-10-19

## 目的与评估范围 / Purpose and Scope

本文记录当前仓库中已经落地的主要能力、仍需处理的工作，以及适合本项目的后续功能建议。状态依据计划文档、源码、测试文件和 Git 历史核对；模板与任务历史功能已通过自动化测试，尚未完成真实服务环境中的手动界面验收或用户需求调研。
This document records the main capabilities present in the repository, remaining work, and follow-on features that fit the project. Status is based on plans, source, tests, and Git history. Templates and task history passed automated tests, but have not received manual UI acceptance in a live service environment or user-demand research.

## 最近更新 / Latest Update

### 2026-10-20：逐行异常复核与修订结果 / 2026-10-20: Row-level exception review and revised results

- **已实现 / Implemented:** 为已完成的合并清洗任务增加逐行异常读取和提交接口；支持修正日期/金额、保留重复或空行、跳过异常行；修订结果另存为 CSV/XLSX，原始文件保持不变。复核状态写入任务摘要，使用版本比较与摘要条件更新避免覆盖并发决定；相同请求重试复用既有修订文件。历史页加入逐行复核、分页、修订和原结果下载入口，行内容只通过安全 DOM 文本节点显示。 / Added row-level exception read and submission APIs for completed merge-and-clean tasks; users can correct dates/amounts, keep duplicate or blank rows, or skip exception rows. Revised CSV/XLSX files are stored separately and the original remains unchanged. Review state lives in the task summary, with version checks and conditional summary updates to prevent concurrent overwrites; identical retries reuse the existing revision. Task history now offers row review, paging, and original/revised downloads, with cell contents rendered through safe DOM text nodes.
- **验证 / Verification:** 后端定向测试 34/34 通过；Node 复核模块测试 3/3 通过；复核模块及页面内联脚本语法检查通过。尚未做手动界面验收。 / Focused backend tests passed 34/34; row-review Node tests passed 3/3; syntax checks passed for the review module and inline page script. No manual UI acceptance has been performed.
- **待做或风险 / Remaining or risks:** 仍需在真实界面验证 CSV/XLSX 修订下载、并发冲突提示、修正输入和大量异常分页。相同映射键仍重复的已修正行会依照已确认规则纳入修订结果。 / Verify CSV/XLSX revision downloads, concurrency conflict messaging, correction inputs, and large-result paging in the live UI. A corrected row whose mapped key remains duplicated is retained, as previously agreed.
- **下一步 / Next:** 完成真实界面验收并修复发现的问题。 / Perform live UI acceptance and address any findings.

### 2026-10-19：智能列映射与格式变化提醒 / 2026-10-19: Smart column mapping and schema drift

- **已实现 / Implemented:** 有类型工作流的配置预览生成版本化表头快照，将模板 ID 和快照纳入预览指纹并保存到任务历史；执行前从 Agent 参数中剥离复用元数据。模板匹配使用 NFKC、大小写与常见分隔符规范化；历史比较可提示新增/缺失列、工作表改名、唯一别名改名候选与歧义候选。界面提示非阻断、不自动改写映射且不显示单元格内容。 / Configured typed-workflow previews generate versioned header snapshots, bind the template ID and snapshot to the preview fingerprint, and persist them in task history; reuse metadata is stripped before Agent execution. Template matching normalizes NFKC, case, and common separators. History comparison can report added/missing columns, renamed worksheets, unique alias-based rename candidates, and ambiguous candidates. UI warnings are non-blocking, do not rewrite mappings, and show no cell values.
- **验证 / Verification:** 已添加后端快照/指纹/执行隔离测试源码及前端规范化/差异测试源码；按用户要求未运行测试，也未做手动界面验收。 / Added backend test source for snapshots, fingerprints, and execution isolation, plus frontend tests for normalization and drift; tests were not run and no manual UI acceptance was performed, per the user's request.
- **待做或风险 / Remaining or risks:** 需要运行后端定向与全套测试、Node 前端测试及模块语法检查；工作表和文件按输入位置比较，输入顺序变动可能产生多条差异提示。 / Run focused and full backend tests, frontend Node tests, and module syntax checks; sheets and files are compared by input position, so reordered inputs can produce multiple drift warnings.
- **下一步 / Next:** 用户安排测试时执行自动化验证；之后再手动验收跨批次复用、改名与歧义提示。 / Run automated verification when the user is ready, then manually accept cross-run reuse, rename, and ambiguity warnings.

### 2026-10-19：CSV 导入导出与数据质量预览 / 2026-10-19: CSV interchange and data quality preview

- **已完成 / Implemented:** CSV 可作为现有摘要、合并清洗、对账和自由模式的单表输入；上传校验支持 UTF-8/BOM 与 GB18030、逗号/制表符/分号识别、引号和多行字段；摘要与合并清洗可选择 CSV 或 XLSX 输出，默认 XLSX；任务历史与结果下载文件名也按输出格式处理。 / CSV is implemented as a single-sheet input for existing summary, merge/cleanup, reconciliation, and freeform workflows; upload validation supports UTF-8/BOM and GB18030, comma/tab/semicolon detection, quoting, and multiline fields; summary and merge/cleanup can export CSV or XLSX, defaulting to XLSX; task history and download filenames follow the selected output format.
- **新增预览 / Added preview checks:** 任务执行预览报告重复表头、空单元格、重复行，以及表头能识别为日期/金额且同时存在可解析与不可解析值的列；只显示计数、列号和最多五个行号样例，不显示单元格内容，不自动修正或阻止任务。日期/金额检查是基于表头提示的启发式检查，无法识别任意命名的字段。 / The run preview reports duplicate headers, empty cells, duplicate rows, and columns whose headers suggest dates or amounts and contain both parseable and unparseable values; it shows counts, column numbers, and up to five row-number samples without cell values, automatic corrections, or blocking the task. Date/amount checks use header hints and may miss fields with arbitrary names.
- **验证 / Verification:** 按用户要求暂不运行自动化测试，也未进行手动 UI 验收；实施计划中的验证项目仍待执行。 / Automated tests were deferred at the user's request, and no manual UI acceptance was performed; verification steps in the implementation plan remain open.
- **待做或风险 / Remaining or risks:** 需执行后端定向与全套测试、前端模块语法检查，并在目标环境确认下载 MIME/文件名；手动验收继续暂缓。 / Run focused and full backend tests and frontend module syntax checks, then confirm download MIME type and filenames in the target environment; manual acceptance remains deferred.
- **下一步 / Next:** 完成 CSV 自动化验证；其后根据真实使用数据选择定时报表或 webhook/API 自动化。 / Complete automated CSV verification; then use real usage data to choose scheduled reports or webhook/API automation.

## 当前进度 / Current Status

### 已完成或已落地 / Implemented

| 范围 / Area | 当前情况 / Current state |
|---|---|
| 产品底座 / Product foundation | Spring Boot SaaS 后端、用户认证、文件上传/下载、套餐与额度、异步任务和静态 Web 界面已存在。 / Spring Boot SaaS backend, user authentication, file upload/download, plans and quotas, asynchronous tasks, and a static web UI are present. |
| CSV 与数据质量 / CSV and data quality | CSV 单表输入已接入现有任务，摘要/合并清洗支持显式 CSV 导出；任务预览提供非阻断式数据质量提示。自动化验证和手动 UI 验收待做。 / Single-sheet CSV input is connected to existing tasks, summary/merge-clean supports explicit CSV export, and task previews provide non-blocking data quality warnings. Automated verification and manual UI acceptance remain open. |
| 智能列映射 / Smart column mapping | 已增加版本化历史表头快照、指纹绑定、保守映射建议和跨批次变化提示；自动化验证与手动 UI 验收待做。 / Added versioned historical header snapshots, fingerprint binding, conservative mapping suggestions, and cross-run drift hints; automated verification and manual UI acceptance remain open. |
| Excel 业务工作流 / Excel business workflows | 多文件任务预览与归属/数量/大小校验、参数指纹、合并清洗、双工作簿对账、分组汇总、生成结果工作簿及结构化异常/摘要已在代码中实现。 / Multi-file task preview and ownership/cardinality/size checks, option fingerprints, merge and cleanup, two-workbook reconciliation, grouped summaries, output workbooks, and structured exceptions/summaries are implemented in code. |
| Agent 与工具边界 / Agent and tool boundary | LangGraph 调用 Java `@AgentTool`；任务文件与规则从后端可信上下文读取，工作簿操作在 Java 工具侧执行，并检查套餐工具权限。 / LangGraph calls Java `@AgentTool` methods; task files and rules come from trusted backend context, workbook operations run in Java tools, and plan tool permissions are checked. |
| 任务可靠性 / Task reliability | PostgreSQL 持久化任务队列和任务输入，数据库事务内校验/消耗额度；调度器派发任务并支持进程重启后恢复。 / PostgreSQL persists queued tasks and task inputs, quota checks/consumption share the database transaction, and a scheduler dispatches and recovers tasks after process restarts. |
| 工程与安全基线 / Engineering and security baseline | Flyway 迁移、Python 锁定依赖、非 root 容器、密钥与内部 Agent 路由保护、请求校验、健康探测、速率限制及 XLSX 上传安全限制已纳入仓库。 / Flyway migrations, locked Python dependencies, non-root containers, secret and internal Agent route protection, request validation, health checks, rate limits, and XLSX upload protections are present. |

### 最近交付 / Recent Delivery

- **内置工作流模板 / Built-in workflow templates:** 已提供月度销售汇总、两表订单对账、多文件去重合并三种模板；只在表头别名唯一命中时预填规则，映射可编辑，并继续要求先预览再确认。 / Added monthly sales summary, two-workbook order reconciliation, and multi-file dedupe merge templates; rules are suggested only for unique header alias matches, remain editable, and still require preview and confirmation.
- **任务历史与规则复用 / Task history and rule reuse:** 已提供按状态、工作流、日期筛选与分页；展示输入文件和摘要、下载结果，并可把旧规则填回表单后换新文件重跑。所有历史查询按当前用户隔离，结果下载复用既有归属校验。 / Added status, workflow, and date filters with pagination; history shows inputs and summaries, supports result downloads, and restores prior rules into the form for a run with new files. History queries are scoped to the authenticated user, and downloads retain existing ownership checks.

这些能力也出现在对应测试中；当前工作位后端 Maven 全套测试已通过，模板规则 Node 测试 6/6 通过。Agent schema 测试此前通过 2/2。 / Corresponding tests cover these capabilities; the backend Maven suite passed in the current worktree, and template-rule Node tests passed 6/6. Agent schema tests previously passed 2/2.

### 计划文档状态 / Plan Document Status

- `2026-10-16-api-hardening.md`：18 项已勾选，0 项未完成。 / `2026-10-16-api-hardening.md`: 18 checked, 0 unchecked.
- `2026-10-16-engineering-baseline.md`：11 项已勾选，0 项未完成。 / `2026-10-16-engineering-baseline.md`: 11 checked, 0 unchecked.
- `2026-10-16-security-baseline.md`：8 项已勾选，0 项未完成。 / `2026-10-16-security-baseline.md`: 8 checked, 0 unchecked.
- `2026-10-16-task-reliability.md`：10 项已勾选，0 项未完成。 / `2026-10-16-task-reliability.md`: 10 checked, 0 unchecked.
- `2026-10-01-excel-business-workflows.md`：已根据当前源码、提交历史和本次验证逐项回填。任务 1–6 的实现/测试/提交状态已勾选；六个无法回溯的“实现前确认失败”历史步骤仍未勾选。任务 7 的界面检查及迁移/手动流程验证仍待完成。 / `2026-10-01-excel-business-workflows.md`: checkboxes were reconciled against source, commit history, and current verification. Implementation/tests/commits for Tasks 1–6 are checked; six unprovable historical “fail before implementation” steps remain unchecked. Task 7 UI checks and migration/manual-flow verification remain open.

### 当前仍需完成 / Remaining Work

1. 完成 Excel 工作流计划中 Task 7 的界面检查、迁移初始化和三类工作流/旧版流程的手动验证；当前环境缺少 Docker 私密配置，尚未运行这些流程。 / Complete Task 7 UI checks, migration initialization, and manual verification of the three workflows plus the legacy flow; these flows have not run because this environment lacks private Docker configuration.
2. 基于真实文件补充模板表头别名，并收集常见工作流、重复运行比例、失败原因、文件规模和付费转化数据。 / Extend template header aliases using real files and collect data on common workflows, repeat-run rates, failure reasons, workbook sizes, and paid conversion.
3. 收集真实使用数据后再调整后续优先级：常见工作流、重复运行比例、失败原因、处理规模和付费转化。当前仓库未提供这些产品分析数据。 / Adjust later priorities using real usage data: common workflows, repeat-run rate, failure reasons, workbook sizes, and paid conversion. The repository does not provide these product analytics today.
4. 检查部署环境和真实用户流程，确认重启恢复、套餐额度、文件下载和三类工作流在目标环境中均符合预期。 / Check the deployment environment and real user flows to confirm restart recovery, plan quotas, downloads, and all three workflows behave as expected in the target environment.

## 市场信号与推荐功能 / Market Signals and Feature Recommendations

这里的“用户多”依据主流产品的功能覆盖和公开社区关注度作粗略判断，不代表本项目用户调研。n8n 官方仓库介绍其提供 400 多种集成，当前 GitHub 页面显示约 20 万颗星；Dify GitHub 仓库显示约 15 万颗星。GitHub 星标是开发者关注度代理指标，不能直接推导终端用户规模或付费意愿。 / “Broadly used” here is estimated from feature coverage in established products and public community interest, not from research with this project's users. The n8n project describes 400+ integrations and its GitHub page shows roughly 200k stars; the Dify repository shows roughly 150k stars. GitHub stars proxy developer interest and do not directly measure end-user adoption or willingness to pay.

| 优先级 / Priority | 建议 / Feature | 用户价值与投入产出 / User value and effort | 建议做法 / Suggested approach |
|---|---|---|---|
| P0（已交付 / Delivered） | 可复用工作流模板 / Reusable workflow templates | 用户反复做月报、销售汇总、库存对账时可少填字段、减少误操作；可复用当前预览和工作流参数，开发成本低。 / Repeated monthly reports, sales summaries, and inventory reconciliation need fewer manual settings and cause fewer mistakes; reuse current preview and workflow options at low implementation cost. | 已提供三种内置模板，仅对唯一别名匹配预填，并保留编辑、预览和确认。 / Three built-in templates now suggest only uniquely matched aliases while retaining editing, preview, and confirmation. |
| P0（已交付 / Delivered） | 任务历史与一键重跑 / Task history and rerun | 财务/运营用户需要追溯结果并对新一期文件重复相同操作；现有持久化任务和结构化摘要可直接支撑。 / Finance and operations users need to find past results and repeat the same operation on a new period's files; persisted tasks and structured summaries provide a foundation. | 已加入任务分页、状态/日期/工作流筛选、结果下载和“复用规则后选择新文件”；接口按当前用户过滤任务，下载继续执行文件归属校验。 / Added task pagination, status/date/workflow filters, result downloads, and “reuse rules with new files”; the API filters by current user and downloads retain file-ownership checks. |
| P1 | 定时或周期性报表 / Scheduled reports | 月报、周报和库存核对具有重复频率；可减少人工操作并提高留存，但需要处理文件来源、时区、额度和失败通知。 / Monthly/weekly reports and inventory checks recur; scheduling reduces manual work and may improve retention, but requires file sources, time zones, quotas, and failure notifications. | 先允许用户指定已保存文件或安全连接的数据源，并在执行前展示计划与额度影响；每次运行保留独立任务记录。 / Initially allow saved files or a securely connected data source, show the schedule and quota impact before activation, and create a separate task record for each run. |
| P1（实现完成，验证待做 / Implemented, verification pending） | CSV 导入与导出 / CSV import and export | CSV 常用于系统间交换和轻量数据整理；对只需要表格数据的用户可以省去工作簿兼容问题。 / CSV is common for data exchange and lightweight cleanup; it avoids workbook compatibility overhead when users only need tabular data. | 已支持 UTF-8/BOM 与 GB18030、逗号/制表符/分号识别、摘要/合并清洗的 CSV 或 XLSX 选择；多表/格式化输出仍用 XLSX。 / Supports UTF-8/BOM and GB18030, comma/tab/semicolon detection, and CSV/XLSX selection for summary and merge/cleanup; multi-sheet or formatted output remains XLSX. |
| P1 | Webhook/API 与自动化平台连接 / Webhook/API and automation-platform connections | n8n 的集成覆盖面说明跨系统触发是成熟的自动化需求；直接做大量 OAuth 连接维护成本高。 / n8n's integration breadth signals established demand for cross-system triggers; maintaining many native OAuth integrations is expensive. | 优先提供有限的任务创建/状态 API、签名 webhook 和示例 n8n 流程；根据真实需求再做飞书/钉钉/Google Drive 等原生连接。 / Prioritize a scoped task API, signed webhooks, and example n8n flows; add native Feishu, DingTalk, or Google Drive connectors only when usage data supports them. |
| P2 | 人工复核与审批 / Human review and approval | 对账差异和清洗异常需要人工判断；当前已有执行前预览和结果异常明细，可逐步增加任务级确认。 / Reconciliation differences and cleanup exceptions need human judgment; existing pre-run preview and result exceptions provide a basis for task-level review. | 先增加“查看差异/异常后确认导出”的单任务步骤，再考虑长时间暂停与恢复机制。LangGraph 的人工介入需要持久化检查点，因此不建议一开始就引入复杂审批流。 / First add a per-task “review differences/exceptions, then confirm export” step; consider long-running pause/resume later. LangGraph human intervention requires persistent checkpoints, so a complex approval workflow is not a first step. |
| P2 | 知识库问答 / Knowledge-base Q&A | 主流 AI 应用平台普遍支持文档检索，但它与本项目现有的确定性 Excel 处理主线距离较远，增加向量索引、文档解析和检索维护成本。 / Document retrieval is common in AI application platforms, but it is farther from this project's deterministic spreadsheet workflow and adds vector indexing, parsing, and retrieval operations. | 只有当目标用户明确需要“结合制度/产品文档解释表格结果”时，再先做小范围文件检索原型。 / Prototype file retrieval only if target users clearly need answers grounded in policy or product documents alongside spreadsheet results. |

## 推荐实施顺序 / Recommended Sequence

1. 完成 CSV 自动化验证；按用户要求暂缓 CSV 与既有工作流的手动 UI 验收。 / Complete automated CSV verification; defer manual UI acceptance for CSV and existing workflows as requested.
2. 收集真实数据，完善内置模板别名，并追踪功能使用率、重复任务比例、失败率、平均文件大小、每任务模型成本和完成任务耗时。 / Gather real usage data, refine built-in template aliases, and track feature adoption, repeat-task rate, failure rate, average file size, model cost per task, and time to completion.
3. 根据重复任务场景和用户反馈，在定时报表与 webhook/API 自动化之间选择下一项。 / Choose the next feature between scheduled reports and webhook/API automation based on recurring-task patterns and user feedback.

## 后续更新约定与记录模板 / Update Convention and Log Template

每次完成一项有用户可见结果的开发或重要基础设施工作后，在本文最上方新增一条日期记录：列出已完成内容、证据或验证结果、未完成/风险、下一步。同步更新对应计划里的复选框；只有实现和约定的验证都完成时才勾选。若工作很小，可在同一次日期记录中合并，不必每个提交都重复写。 / After each user-visible feature or substantial infrastructure change, add a dated entry near the top of this document covering what changed, evidence or verification, remaining work/risks, and next steps. Update the corresponding plan checkboxes at the same time; check an item only after implementation and its agreed verification are complete. Small changes may be grouped in one dated entry rather than documented for every commit.

### 更新记录 / Update Log

#### 2026-10-19：任务历史与规则复用 / 2026-10-19: Task history and rule reuse

- **已完成 / Done:** 新增按当前用户隔离的任务历史 API 与列表；支持状态、工作流、日期筛选和分页，展示输入文件及结果摘要，下载结果，并将旧规则恢复到新任务表单。 / Added an owner-scoped task-history API and list with status, workflow, and date filters and pagination; it shows input files and summaries, downloads results, and restores saved rules into a new task form.
- **验证 / Verification:** 后端 Maven 全套测试 36/36 通过；任务历史服务/控制器/数据层测试通过；模板 Node 测试 6/6 和静态页面模块语法检查通过。 / Full backend Maven suite passed 36/36; task-history service/controller/repository tests passed; template Node tests passed 6/6 and static page module syntax check passed.
- **待做或风险 / Remaining or risks:** 当前环境仍缺 Docker 私密配置，因此没有执行真实服务中的任务历史 UI 与数据库迁移手动验收。 / Docker private configuration remains unavailable, so task-history UI and database migration have not been manually accepted against a live service.
- **下一步 / Next:** 完成 Excel 工作流和任务历史的手动验收；收集使用数据后完善模板及 CSV 导入/导出优先级。 / Manually accept the Excel workflows and task history; refine templates and CSV import/export priorities using usage data.

#### 2026-10-19：内置模板首版 / 2026-10-19: Built-in template release

- **已完成 / Done:** 在现有表单加入月度销售汇总、两表订单对账、多文件去重合并模板；按唯一表头别名预填可编辑字段，保留执行预览与确认步骤。 / Added monthly sales summary, two-workbook reconciliation, and multi-file dedupe merge templates to the existing form; uniquely matched header aliases prefill editable fields, with preview and confirmation retained.
- **验证 / Verification:** 模板规则测试 6/6 通过；静态页面模块语法检查通过；后端 Maven 测试通过。 / Template rule tests: 6/6 passed; static page module syntax check passed; backend Maven tests passed.
- **待做或风险 / Remaining or risks:** Docker 私密配置不可用，未完成 Flyway 初始化和手动 UI 流程验收；表头别名需根据实际用户文件持续补充。 / Private Docker configuration was unavailable, so Flyway initialization and manual UI acceptance remain open; header aliases should evolve based on real user files.
- **下一步 / Next:** 完成 Excel 工作流手动验收，再实现任务历史与一键重跑。 / Complete manual acceptance of Excel workflows, then implement task history and one-click rerun.

#### 2026-10-19：首次项目盘点 / 2026-10-19: Initial project review

- **已完成 / Done:** 盘点当前计划、源码、测试文件及 Git 历史；建立功能建议和持续更新格式。 / Reviewed plans, source, test files, and Git history; added feature recommendations and a recurring update format.
- **验证 / Verification:** 静态检查；未启动服务，未运行测试。 / Static review; services were not started and tests were not run.
- **待做 / Remaining:** 回填 Excel 工作流计划，执行约定的后端/Agent/迁移验证，并结合用户数据调整优先级。 / Reconcile the Excel workflow plan, run the agreed backend/Agent/migration checks, and adjust priorities using user data.
- **下次更新 / Next update:** 记录 Excel 计划验收结果或首个新功能交付。 / Record the Excel plan acceptance results or the first delivery of a new feature.

#### 2026-10-19：基线核对 / 2026-10-19: Baseline verification

- **已完成 / Done:** 修正 TaskService 测试与持久化队列架构不匹配；排除 EasyExcel 带入的 POI 4.x schema 包，解决 POI 5.5.1 工作簿生成冲突。 / Aligned TaskService tests with durable queue dispatch; excluded EasyExcel's POI 4.x schema bundle to resolve workbook generation conflicts with POI 5.5.1.
- **验证 / Verification:** 后端测试 32/32 通过；Agent schema 测试 2/2 通过；当前未执行 Flyway 初始化和手动界面流程。 / Backend tests: 32/32 passed; Agent schema tests: 2/2 passed; Flyway initialization and manual UI flows were not run.
- **待做 / Remaining:** 完成工作流 UI 检查和迁移验证，再继续新增功能。 / Complete workflow UI checks and migration verification before proceeding with new features.
- **下一步 / Next:** 设计并实现 P0 内置工作流模板。 / Design and implement P0 built-in workflow templates.

#### 可复制模板 / Copyable entry template

```markdown
#### YYYY-MM-DD：本次工作标题 / YYYY-MM-DD: Work title

- **已完成 / Done:** ...
- **验证 / Verification:** ...
- **待做或风险 / Remaining or risks:** ...
- **下一步 / Next:** ...
```

## 资料来源 / Sources

- n8n 官方仓库与集成说明 / n8n official repository and integrations: [GitHub repository](https://github.com/n8n-io/n8n), [n8n documentation](https://docs.n8n.io/).
- n8n 工具调用人工审批 / n8n human approval for tool calls: [Human-in-the-loop for AI tool calls](https://docs.n8n.io/advanced-ai/human-in-the-loop-tools/).
- Dify 开源项目与文档应用能力 / Dify project and document application capabilities: [Dify on GitHub](https://github.com/langgenius/dify), [Dify quick start](https://docs.dify.ai/en/guides/application-orchestrate/creating-an-application).
- LangGraph 人工介入与持久化检查点 / LangGraph human intervention and persistent checkpoints: [Thinking in LangGraph](https://docs.langchain.com/oss/javascript/langgraph/thinking-in-langgraph).

公开星标数会变化，应视为本次检索时的粗略快照；GitHub 星标也不是终端用户量。 / Public star counts change and should be treated as a rough snapshot at lookup time; GitHub stars are not end-user counts.
