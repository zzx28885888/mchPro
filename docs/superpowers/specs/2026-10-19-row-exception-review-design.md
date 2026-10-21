# 逐行异常复核与结果对比设计 / Row-Level Exception Review and Result Comparison Design

**日期 / Date:** 2026-10-19
**状态 / Status:** 设计草案，待用户审阅 / Draft, pending user review

## 目标 / Goal

让用户在合并清洗任务完成后，逐行检查被标记的异常，比较来源行与自动处理结果，并决定修正、保留或跳过；复核后生成单独的修订结果，原始自动处理结果继续可下载。
After a merge-and-clean task completes, let users inspect flagged rows, compare source rows with the automated output, and choose to correct, keep, or skip them; create a separate revised result while retaining the original automated result for download.

## 当前状态与边界 / Current State and Scope

合并清洗工作流已产生带来源文件、工作表、行号、列名和异常值的 `RowException`，并在 `resultSummary.exceptions` 中保留摘要。当前日期/金额异常行会保留原值，重复行和空行可被跳过；界面只展示文本摘要，没有逐行复核或重建结果的接口。
The merge-and-clean workflow already emits `RowException` records with source file, worksheet, row, column, and issue value, and stores a summary in `resultSummary.exceptions`. Invalid date/amount rows currently retain the raw value, while duplicate and blank rows may be skipped; the UI only displays a text summary and has no row review or result-rebuild API.

本次仅覆盖已完成的 `MERGE_CLEAN` 任务，以及具有来源行号的 `INVALID_DATE`、`INVALID_AMOUNT`、`DUPLICATE_ROW_SKIPPED`、`BLANK_ROW_SKIPPED` 异常。第 1 行表头映射异常不是逐行异常，不进入复核队列。每条来源行只允许一个最终复核动作；同一行有多个异常时合并展示。
This feature covers only completed `MERGE_CLEAN` tasks and row-addressable `INVALID_DATE`, `INVALID_AMOUNT`, `DUPLICATE_ROW_SKIPPED`, and `BLANK_ROW_SKIPPED` issues. Header-mapping issues on row 1 are not row-level issues and are excluded. Each source row receives one final review action; multiple issues on the same row are grouped together.

## 用户流程 / User Flow

1. 用户从任务历史打开已完成的合并清洗任务并选择“复核异常”。没有符合范围的异常时显示空状态。
   The user opens a completed merge-and-clean task from history and selects “Review exceptions.” Show an empty state when no supported issues exist.
2. 复核列表按来源文件、工作表和行号展示异常原因、原始整行，以及它在原始自动处理结果中的对应行；若该行被自动跳过，明确说明当前结果中没有对应行。
   The review list groups by source file, worksheet, and row number, showing the issue, the original row, and its counterpart in the original automated result; clearly state when an automatically skipped row has no output counterpart.
3. 用户可对行选择“修正”“保留”或“跳过”。“修正”仅对日期/金额异常开放，可一次修改该行一项或多项被报告的问题单元格，不允许改动其他单元格；修正会将该行纳入修订结果，即使它仍与已输出行重复。“保留”将来源行按现有映射和清洗规则纳入修订结果，保留无法规范化的原值；“跳过”确保来源行不出现在修订结果中。
   The user chooses “Correct,” “Keep,” or “Skip” for each row. “Correct” is available only for date/amount issues and may correct one or more reported issue cells on that row, but no other cells; it includes the corrected row in the revised result even if it still duplicates an output row. “Keep” includes the source row using existing mapping and cleanup rules, retaining an unparseable raw value; “Skip” ensures the source row is absent from the revised result.
4. 保存复核时，服务端重新读取原任务输入并重新运行确定性合并清洗规则，应用复核动作，生成新的结果文件。原始结果文件保持不变；任务历史同时提供原始和修订版下载入口。
   On save, the server rereads the original task inputs, reruns deterministic merge-and-clean rules with the review decisions, and creates a new result file. The original result remains unchanged; task history offers downloads for both original and revised results.
5. 用户再次打开任务时，能看到已保存的决定和最新修订版。修改决定后重新生成修订版；同一版本的重复提交应幂等。
   On reopening the task, the user sees saved decisions and the latest revised result. Changing decisions regenerates the revised result; resubmitting the same review version is idempotent.

## 数据与接口设计 / Data and API Design

- 新增任务复核读取接口和复核提交接口，路由置于 `/api/tasks/{id}/review`。读取和提交均要求任务属于当前用户、状态为 `COMPLETED` 且工作流为 `MERGE_CLEAN`。
  Add task review read and submit endpoints under `/api/tasks/{id}/review`. Both require the task to belong to the authenticated user, have `COMPLETED` status, and use `MERGE_CLEAN`.
- 复核读取响应只返回有界分页的异常条目；条目标识由 task ID、来源文件 ID、sheet、行号和异常类别确定。需要显示行数据时，从用户拥有的原任务输入和原始结果文件实时读取，不把整行单元格内容复制进任务摘要或复核状态。
  The read response returns a bounded page of issue items. An item is identified by task ID, source file ID, sheet, row number, and issue code. Read row data from the task's owned source inputs and original result file on demand; do not copy full rows into task summaries or review state.
- 复核提交只接受已知条目标识、动作和可选修正值映射；修正仅限该行被报告的日期/金额单元格，并按任务已保存的格式与映射规则验证。拒绝不存在的条目、跨文件条目、重复且冲突的决定、超长输入和不支持的动作。
  Review submission accepts only known item IDs, actions, and an optional map of corrections. Corrections are limited to reported date/amount cells on that row and validated against the task's saved format and mapping rules. Reject unknown or cross-file items, conflicting duplicate decisions, oversized input, and unsupported actions.
- 以版本化 `review` 对象把动作、修正值、修订文件 ID、版本号和更新时间保存在现有任务 `resultSummary` JSON 中，不增加独立复核表。状态更新必须基于当前版本做乐观并发检查，避免同时提交互相覆盖。
  Persist actions, correction values, revised file ID, version, and update time in a versioned `review` object inside the existing task `resultSummary` JSON; do not add a separate review table. Apply optimistic version checks so concurrent submissions cannot silently overwrite each other.
- 原始 `resultFileId` 不修改；复核后的文件 ID 存在 `resultSummary.review.revisedFileId`，通过现有的文件下载归属校验下载。失败时不覆盖原摘要或原结果，并清理未关联的临时新文件。
  Do not change the original `resultFileId`; store the reviewed file ID at `resultSummary.review.revisedFileId` and download it through existing file-ownership checks. On failure, preserve the original summary and result and clean up any newly created but unlinked file.

## 处理语义 / Decision Semantics

- `CORRECT`: 对该行一个或多个 `INVALID_DATE` / `INVALID_AMOUNT` 单元格写入用户修正值，调用既有确定性转换逻辑重新生成并纳入该行；若任一值仍无效，返回可读校验错误且不保存决定。
  `CORRECT`: apply user corrections to one or more `INVALID_DATE` / `INVALID_AMOUNT` cells on the row and regenerate and include it through existing deterministic transformations; if any value remains invalid, return a clear validation error and do not save the decision.
- `KEEP`: 将该来源行包含在修订版。有效值按现有规则规范化；无法规范化的异常值以来源原值保留。重复行可按用户选择保留。
  `KEEP`: include the source row in the revised result. Valid values are normalized by existing rules; unparseable issue values remain as supplied. Duplicate rows may be retained when selected.
- `SKIP`: 从修订版排除该来源行。
  `SKIP`: exclude the source row from the revised result.
- 未提交复核决定的异常行保持自动工作流的既有结果行为。修订版计数和异常摘要根据最终行集合重新计算；原始结果摘要不变。
  Rows without a submitted decision keep the original automatic workflow behavior. Recalculate revised counts and issue summaries from the final row set; leave the original result summary unchanged.

## 安全、隐私与兼容 / Security, Privacy, and Compatibility

- 所有读取和写入都执行任务归属校验；源文件和结果文件仍使用既有 `FileController` 用户归属验证。
  Enforce task ownership for all reads and writes; source and result downloads continue to use the existing `FileController` ownership checks.
- 复核页面通过 `textContent` 等安全 DOM API 展示文件名、表头和单元格；不把文件内容写入日志。返回的行字段只限当前用户有权访问的原任务文件。
  Render filenames, headers, and cells with safe DOM APIs such as `textContent`; do not log workbook content. Returned row data is limited to original task files the current user may access.
- 复核状态只保存必要的动作和用户提交的修正值，不缓存整行原始数据。原有任务、历史结果和无异常任务行为保持兼容。
  Review state stores only necessary actions and user-entered corrections, not full source rows. Existing tasks, history results, and tasks without supported issues remain compatible.
- 不改变原始处理结果或任务完成状态；复核失败不得影响原始结果的下载。
  Do not change the original automated result or task completion status; review failures must not affect access to the original result.

## 非目标 / Non-Goals

- 不对 `FREEFORM`、`SUMMARY`、`RECONCILE` 工作流增加逐行复核。
  Do not add row review to `FREEFORM`, `SUMMARY`, or `RECONCILE` workflows.
- 不增加多人审批、任务暂停/恢复、角色权限、评论或定时复核。
  Do not add multi-user approval, task pause/resume, role permissions, comments, or scheduled review.
- 不允许编辑整行或未标记的其他单元格，不通过模型猜测修正值。
  Do not allow whole-row or unflagged-cell editing, and do not use a model to guess corrections.
- 不删除或替换原始结果文件。
  Do not delete or replace the original result file.

## 验收标准 / Acceptance Criteria

1. 只有当前用户拥有且已完成的 `MERGE_CLEAN` 任务可读取或提交复核；其他任务返回适当的 404/400 错误。
   Only completed `MERGE_CLEAN` tasks owned by the current user can be read or reviewed; other tasks return appropriate 404/400 errors.
2. 异常清单按来源行分组，并能展示来源行和原始输出对应行；已跳过行明确显示无输出对应项。
   Issues are grouped by source row and show source/output counterparts; skipped rows clearly show that no output counterpart exists.
3. 日期/金额异常的有效修正会进入修订版；无效修正被拒绝且不保存。
   Valid date/amount corrections appear in the revised output; invalid corrections are rejected and not persisted.
4. 保留/跳过决定对异常行和重复行按定义生效；未决定行维持原有行为。
   Keep/skip decisions work as defined for issue rows and duplicates; undecided rows retain their original behavior.
5. 原始下载不变，修订版文件可单独下载；复核状态可在任务历史重载后恢复，重复提交不产生重复修订文件。
   The original download is unchanged, the revised file downloads separately, review state restores from task history, and repeated submissions do not create duplicate revisions.
6. 后端测试源码覆盖归属、状态/工作流限制、动作校验、并发版本、修订输出和失败回滚；前端测试或可验证的交互检查覆盖安全显示及决定状态恢复。按用户之前要求，实现阶段先不执行测试，除非用户另行要求。
   Backend test source covers ownership, status/workflow restrictions, decision validation, version concurrency, revised outputs, and rollback; frontend tests or verifiable interaction checks cover safe rendering and decision restoration. Per the user's earlier request, tests are not run during implementation unless the user asks.

## 风险与待确认实现细节 / Risks and Implementation Details

- 输入文件和工作表可在原任务完成后被删除或无法读取时，复核应返回明确错误并保留原始结果。
  If an input file or worksheet is deleted or unreadable after task completion, review must return a clear error while retaining the original result.
- 任务结果摘要目前由任务完成操作写入；实现需增加受限、带版本检查的摘要更新方法，不能允许客户端直接提交任意摘要 JSON。
  Task summaries are currently written on task completion; implementation must add a restricted summary update with version checks and must not let clients submit arbitrary summary JSON.
- 修订文件的创建与任务摘要更新跨文件存储和数据库，不能天然原子提交；实现需采用先生成临时/新文件、再条件更新摘要、失败时清理未关联文件的顺序。
  Revised-file creation and summary updates span file storage and the database and are not inherently atomic; create a new file, conditionally update the summary, and clean up unlinked files on failure.
