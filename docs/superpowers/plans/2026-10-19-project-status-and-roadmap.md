# 项目进度与功能路线图 / Project Status and Feature Roadmap

**更新日期：** 2026-10-19
**Updated:** 2026-10-19

## 目的与评估范围 / Purpose and Scope

本文记录当前仓库中已经落地的主要能力、仍需处理的工作，以及适合本项目的后续功能建议。状态依据当前分支的计划文档、源码、测试文件和 Git 提交记录进行静态核查；本次没有启动服务或运行测试，因此不代表生产环境验收或实际用户需求数据。
This document records the main capabilities present in the repository, remaining work, and follow-on features that fit the project. Status is based on a static review of plan documents, source code, test files, and Git history on the current branch. Services and tests were not run for this review, so this is not production acceptance or measured user-demand data.

## 当前进度 / Current Status

### 已完成或已落地 / Implemented

| 范围 / Area | 当前情况 / Current state |
|---|---|
| 产品底座 / Product foundation | Spring Boot SaaS 后端、用户认证、文件上传/下载、套餐与额度、异步任务和静态 Web 界面已存在。 / Spring Boot SaaS backend, user authentication, file upload/download, plans and quotas, asynchronous tasks, and a static web UI are present. |
| Excel 业务工作流 / Excel business workflows | 多文件任务预览与归属/数量/大小校验、参数指纹、合并清洗、双工作簿对账、分组汇总、生成结果工作簿及结构化异常/摘要已在代码中实现。 / Multi-file task preview and ownership/cardinality/size checks, option fingerprints, merge and cleanup, two-workbook reconciliation, grouped summaries, output workbooks, and structured exceptions/summaries are implemented in code. |
| Agent 与工具边界 / Agent and tool boundary | LangGraph 调用 Java `@AgentTool`；任务文件与规则从后端可信上下文读取，工作簿操作在 Java 工具侧执行，并检查套餐工具权限。 / LangGraph calls Java `@AgentTool` methods; task files and rules come from trusted backend context, workbook operations run in Java tools, and plan tool permissions are checked. |
| 任务可靠性 / Task reliability | PostgreSQL 持久化任务队列和任务输入，数据库事务内校验/消耗额度；调度器派发任务并支持进程重启后恢复。 / PostgreSQL persists queued tasks and task inputs, quota checks/consumption share the database transaction, and a scheduler dispatches and recovers tasks after process restarts. |
| 工程与安全基线 / Engineering and security baseline | Flyway 迁移、Python 锁定依赖、非 root 容器、密钥与内部 Agent 路由保护、请求校验、健康探测、速率限制及 XLSX 上传安全限制已纳入仓库。 / Flyway migrations, locked Python dependencies, non-root containers, secret and internal Agent route protection, request validation, health checks, rate limits, and XLSX upload protections are present. |

这些能力也出现在对应的测试源码中，例如 `ExcelWorkflowServiceTest`、`TaskServiceTest`、`AgentToolControllerTest` 和 Agent 工具 schema 测试。本次仅核对了测试文件及用例，不代表这些测试在当前环境已通过。
Corresponding test source exists, including `ExcelWorkflowServiceTest`, `TaskServiceTest`, `AgentToolControllerTest`, and Agent tool-schema tests. This review checked the files and cases only; it does not claim they pass in the current environment.

### 计划文档状态 / Plan Document Status

- `2026-10-16-api-hardening.md`：18 项已勾选，0 项未完成。 / `2026-10-16-api-hardening.md`: 18 checked, 0 unchecked.
- `2026-10-16-engineering-baseline.md`：11 项已勾选，0 项未完成。 / `2026-10-16-engineering-baseline.md`: 11 checked, 0 unchecked.
- `2026-10-16-security-baseline.md`：8 项已勾选，0 项未完成。 / `2026-10-16-security-baseline.md`: 8 checked, 0 unchecked.
- `2026-10-16-task-reliability.md`：10 项已勾选，0 项未完成。 / `2026-10-16-task-reliability.md`: 10 checked, 0 unchecked.
- `2026-10-01-excel-business-workflows.md`：38 项仍未勾选；但该计划描述的核心能力在当前源码和测试中已能找到，且 Git 历史中有工作流、汇总和 UI 实现提交。该计划的勾选状态已落后于代码，应在下一次计划维护时逐项对照验收记录回填，不能只凭本次静态检查声称全部验收完成。 / `2026-10-01-excel-business-workflows.md`: all 38 items remain unchecked, although the core capabilities described by the plan are present in source and tests, and Git history contains workflow, summary, and UI implementation commits. Its checkboxes are stale relative to the code. Reconcile them item by item against acceptance evidence during the next plan update; this static review alone does not establish full acceptance.

### 当前仍需完成 / Remaining Work

1. 回填 Excel 工作流计划：逐项核对 38 个复选框，区分“实现已存在”“验证已完成”“仍需实现”，并记录缺失的验收证据。 / Reconcile the Excel workflow plan: review all 38 checkboxes and distinguish implemented, verified, and still-unimplemented items; record any missing acceptance evidence.
2. 在本地或 CI 实际运行后端及 Agent 测试、构建和迁移检查；本次没有运行这些命令。 / Run backend and Agent tests, builds, and migration checks locally or in CI; these commands were not run for this review.
3. 收集真实使用数据后再确定新增功能优先级：常见工作流、重复运行比例、失败原因、处理规模和付费转化。当前仓库未提供这些产品分析数据。 / Use real usage data to set feature priorities: common workflows, repeat-run rate, failure reasons, workbook sizes, and paid conversion. The repository does not provide these product analytics today.
4. 检查部署环境和真实用户流程，确认重启恢复、套餐额度、文件下载和三类工作流在目标环境中均符合预期。 / Check the deployment environment and real user flows to confirm restart recovery, plan quotas, downloads, and all three workflows behave as expected in the target environment.

## 市场信号与推荐功能 / Market Signals and Feature Recommendations

这里的“用户多”依据主流产品的功能覆盖和公开社区关注度作粗略判断，不代表本项目用户调研。n8n 官方仓库介绍其提供 400 多种集成，当前 GitHub 页面显示约 20 万颗星；Dify GitHub 仓库显示约 15 万颗星。GitHub 星标是开发者关注度代理指标，不能直接推导终端用户规模或付费意愿。 / “Broadly used” here is estimated from feature coverage in established products and public community interest, not from research with this project's users. The n8n project describes 400+ integrations and its GitHub page shows roughly 200k stars; the Dify repository shows roughly 150k stars. GitHub stars proxy developer interest and do not directly measure end-user adoption or willingness to pay.

| 优先级 / Priority | 建议 / Feature | 用户价值与投入产出 / User value and effort | 建议做法 / Suggested approach |
|---|---|---|---|
| P0 | 可复用工作流模板 / Reusable workflow templates | 用户反复做月报、销售汇总、库存对账时可少填字段、减少误操作；可复用当前预览和工作流参数，开发成本低。 / Repeated monthly reports, sales summaries, and inventory reconciliation need fewer manual settings and cause fewer mistakes; reuse current preview and workflow options at low implementation cost. | 先做内置模板，例如“月度销售汇总”“两表订单对账”“多文件去重合并”；模板保存字段映射、维度和清洗规则，提交前仍显示预览。 / Start with built-in templates such as monthly sales summary, two-file order reconciliation, and multi-file deduplicating merge; store mappings, dimensions, and cleanup rules while retaining pre-run preview. |
| P0 | 任务历史与一键重跑 / Task history and rerun | 财务/运营用户需要追溯结果并对新一期文件重复相同操作；当前持久化任务和结构化摘要可直接支撑。 / Finance and operations users need to find past results and repeat the same operation on a new period's files; persisted tasks and structured summaries provide a foundation. | 提供任务列表、状态/时间/工作流筛选、结果下载和“复制规则后换文件重跑”；下载链接继续做用户归属校验。 / Add task list, status/date/workflow filters, result downloads, and “reuse rules with new files”; retain file ownership checks for downloads. |
| P1 | 定时或周期性报表 / Scheduled reports | 月报、周报和库存核对具有重复频率；可减少人工操作并提高留存，但需要处理文件来源、时区、额度和失败通知。 / Monthly/weekly reports and inventory checks recur; scheduling reduces manual work and may improve retention, but requires file sources, time zones, quotas, and failure notifications. | 先允许用户指定已保存文件或安全连接的数据源，并在执行前展示计划与额度影响；每次运行保留独立任务记录。 / Initially allow saved files or a securely connected data source, show the schedule and quota impact before activation, and create a separate task record for each run. |
| P1 | CSV 导入与导出 / CSV import and export | CSV 常用于系统间交换和轻量数据整理；对只需要表格数据的用户可以省去工作簿兼容问题。 / CSV is common for data exchange and lightweight cleanup; it avoids workbook compatibility overhead when users only need tabular data. | 先支持 UTF-8 CSV、分隔符/编码识别和明确的导出选择；保留 XLSX 为需要多工作表、格式或图表的输出。 / Start with UTF-8 CSV, delimiter/encoding detection, and an explicit export choice; retain XLSX for multiple sheets, formatting, or charts. |
| P1 | Webhook/API 与自动化平台连接 / Webhook/API and automation-platform connections | n8n 的集成覆盖面说明跨系统触发是成熟的自动化需求；直接做大量 OAuth 连接维护成本高。 / n8n's integration breadth signals established demand for cross-system triggers; maintaining many native OAuth integrations is expensive. | 优先提供有限的任务创建/状态 API、签名 webhook 和示例 n8n 流程；根据真实需求再做飞书/钉钉/Google Drive 等原生连接。 / Prioritize a scoped task API, signed webhooks, and example n8n flows; add native Feishu, DingTalk, or Google Drive connectors only when usage data supports them. |
| P2 | 人工复核与审批 / Human review and approval | 对账差异和清洗异常需要人工判断；当前已有执行前预览和结果异常明细，可逐步增加任务级确认。 / Reconciliation differences and cleanup exceptions need human judgment; existing pre-run preview and result exceptions provide a basis for task-level review. | 先增加“查看差异/异常后确认导出”的单任务步骤，再考虑长时间暂停与恢复机制。LangGraph 的人工介入需要持久化检查点，因此不建议一开始就引入复杂审批流。 / First add a per-task “review differences/exceptions, then confirm export” step; consider long-running pause/resume later. LangGraph human intervention requires persistent checkpoints, so a complex approval workflow is not a first step. |
| P2 | 知识库问答 / Knowledge-base Q&A | 主流 AI 应用平台普遍支持文档检索，但它与本项目现有的确定性 Excel 处理主线距离较远，增加向量索引、文档解析和检索维护成本。 / Document retrieval is common in AI application platforms, but it is farther from this project's deterministic spreadsheet workflow and adds vector indexing, parsing, and retrieval operations. | 只有当目标用户明确需要“结合制度/产品文档解释表格结果”时，再先做小范围文件检索原型。 / Prototype file retrieval only if target users clearly need answers grounded in policy or product documents alongside spreadsheet results. |

## 推荐实施顺序 / Recommended Sequence

1. 先完成 Excel 工作流计划回填和完整验证，明确当前基线。 / Reconcile the Excel workflow plan and run full verification to establish a reliable baseline.
2. 优先交付内置模板与任务历史/重跑；这两项直接复用现有任务、预览、参数和结果模型。 / Deliver built-in templates and task history/rerun first; both reuse the existing task, preview, options, and result model.
3. 加入 CSV 导入/导出，再依据使用场景选择定时报表或 API/webhook。 / Add CSV import/export, then choose scheduled reports or API/webhooks based on usage patterns.
4. 以真实数据决定下一阶段：记录功能使用率、重复任务比例、失败率、平均文件大小、每任务模型成本及用户完成任务所需时间。 / Use real data to choose the next phase: track feature adoption, repeat-task rate, failure rate, average file size, model cost per task, and time to task completion.

## 后续更新约定与记录模板 / Update Convention and Log Template

每次完成一项有用户可见结果的开发或重要基础设施工作后，在本文最上方新增一条日期记录：列出已完成内容、证据或验证结果、未完成/风险、下一步。同步更新对应计划里的复选框；只有实现和约定的验证都完成时才勾选。若工作很小，可在同一次日期记录中合并，不必每个提交都重复写。 / After each user-visible feature or substantial infrastructure change, add a dated entry near the top of this document covering what changed, evidence or verification, remaining work/risks, and next steps. Update the corresponding plan checkboxes at the same time; check an item only after implementation and its agreed verification are complete. Small changes may be grouped in one dated entry rather than documented for every commit.

### 更新记录 / Update Log

#### 2026-10-19：首次项目盘点 / 2026-10-19: Initial project review

- **已完成 / Done:** 盘点当前计划、源码、测试文件及 Git 历史；建立功能建议和持续更新格式。 / Reviewed plans, source, test files, and Git history; added feature recommendations and a recurring update format.
- **验证 / Verification:** 静态检查；未启动服务，未运行测试。 / Static review; services were not started and tests were not run.
- **待做 / Remaining:** 回填 Excel 工作流计划，执行约定的后端/Agent/迁移验证，并结合用户数据调整优先级。 / Reconcile the Excel workflow plan, run the agreed backend/Agent/migration checks, and adjust priorities using user data.
- **下次更新 / Next update:** 记录 Excel 计划验收结果或首个新功能交付。 / Record the Excel plan acceptance results or the first delivery of a new feature.

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
