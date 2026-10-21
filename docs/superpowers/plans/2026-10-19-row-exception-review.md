# 逐行异常复核与结果对比实施计划 / Row-Level Exception Review and Result Comparison Implementation Plan

> **For agentic workers / 执行人员：** 按任务逐项实现；优先使用 `superpowers:executing-plans`。步骤用复选框跟踪。 / Implement task by task; prefer `superpowers:executing-plans`. Track steps with checkboxes.

**Goal:** 在已完成的合并清洗任务中提供逐行异常复核、受限修正和修订版导出，并保留原结果。 / Add row-level issue review, constrained corrections, and revised export to completed merge-and-clean tasks while retaining the original result.

**Architecture:** 以确定性决策扩展现有合并清洗服务；新增任务归属受限的复核 API，从原任务输入重建预览与修订版。复核决定及修订文件 ID 放入现有 `resultSummary` JSON，通过条件更新处理并发；历史任务原始结果不变。 / Extend the deterministic merge-and-clean service with review decisions; add owner-scoped review APIs that rebuild rows and revised output from original task inputs. Store decisions and revised file ID in the existing `resultSummary` JSON with conditional updates for concurrency; preserve original task results.

**Tech Stack:** Java 21, Spring Boot, MyBatis-Plus/JDBC, existing Excel/CSV services, static JavaScript modules, JUnit 5/Mockito, Node test runner. / Java 21, Spring Boot, MyBatis-Plus/JDBC, existing Excel/CSV services, static JavaScript modules, JUnit 5/Mockito, and Node test runner.

**Spec:** `docs/superpowers/specs/2026-10-19-row-exception-review-design.md`

## Global Constraints / 全局约束

- 仅支持 `COMPLETED` 的 `MERGE_CLEAN` 任务和 `INVALID_DATE`、`INVALID_AMOUNT`、`DUPLICATE_ROW_SKIPPED`、`BLANK_ROW_SKIPPED` 异常。 / Support only `COMPLETED` `MERGE_CLEAN` tasks and the four issue codes defined in the spec.
- 行身份由来源文件 ID、工作表名和一基行号组成；来源行数据实时读取，不复制进复核状态。 / Identify rows by source file ID, worksheet name, and one-based row number; read source rows on demand and never copy them into review state.
- 原始 `resultFileId` 与任务状态不可变；修订版通过现有文件归属校验下载。 / Keep original `resultFileId` and task status immutable; download revised files through existing ownership checks.
- 不增加数据库迁移或运行期依赖；不将工作簿内容写入日志。 / Add no database migration or runtime dependency; never log workbook contents.
- 新增或修改的文档、注释和配置注释均须中英双语；避免无必要注释。 / Keep all changed documentation, source comments, and configuration comments bilingual; avoid unnecessary comments.
- 按用户此前要求，先补测试源码和实现，但不执行测试；用户之后要求测试时再运行。 / Per the user's earlier request, add test source and implementation but do not execute tests until asked.

## Review Focus / 审查重点

- 同一来源行有多个可修正异常时，修正映射需完整覆盖全部已报告单元格；遗漏或改动未报告列应被拒绝。 / Multiple correctable issues on one source row require corrections for all reported cells; missing corrections or edits to unreported columns must be rejected.
- 重复行默认跳过；选择保留时只把该来源行加入修订版，不改变原结果。 / Duplicate rows remain skipped by default; keeping one adds only that source row to the revised output and never alters the original.
- 空行、缺少对应输出行的异常、工作表消失或原始文件不可读时，复核状态和原始结果必须保持安全。 / Blank rows, issues without output counterparts, missing worksheets, and unreadable original files must not corrupt review state or the original result.
- 非任务所有者、非完成任务、其他工作流及过期版本请求必须失败，且不得泄露源单元格内容。 / Non-owners, unfinished tasks, other workflows, and stale versions must fail without exposing source cells.
- 并发或重试提交不得覆盖较新决定或创建重复的修订文件；导出/摘要更新失败须清理未关联文件。 / Concurrent or retried submissions must not overwrite newer decisions or create duplicate revisions; export/summary failures must clean up unlinked files.

---

### Task 1: Deterministic row decision model / 任务 1：确定性逐行决定模型

**Files / 文件：**

- Create: `backend/src/main/java/com/excelai/excel/MergeReviewDecision.java`
- Modify: `backend/src/main/java/com/excelai/excel/ExcelWorkflowService.java`
- Test: `backend/src/test/java/com/excelai/excel/ExcelWorkflowServiceTest.java`

**Interfaces / 接口：**

- `MergeReviewDecision.SourceRowKey(Long fileId, String sheetName, int rowNumber)` identifies one source row.
- `MergeReviewDecision.Decision(Action action, Map<String,String> corrections)` uses `CORRECT`, `KEEP`, or `SKIP`; correction keys are output columns named by the row's reported date/amount issues.
- Add `mergeAndClean(List<SheetInput>, MergeOptions, Map<SourceRowKey,Decision>)`; retain the existing overload and make it delegate with an empty decision map.

- [x] Add tests for default behavior, keep/skip on duplicate and blank rows, and correction of both date and amount issues on one row. / 测试源码已添加并通过。
- [x] Implement row-key validation and decisions without changing existing behavior when the decision map is empty. / 已完成
- [x] Reject unknown correction columns and invalid corrected values before returning a revised result. / 已完成

**Expected behavior / 预期行为：** `SKIP` excludes the row and increments `reviewedRowsSkipped`; `KEEP` includes its current mapped values even when default cleanup skips it; `CORRECT` requires corrections for every supported invalid date/amount issue on that row, routes values through existing normalization, and includes the corrected row even if its key remains duplicated.

### Task 2: Shared workflow options and review read model / 任务 2：共享规则解析与复核读取模型

**Files / 文件：**

- Create: `backend/src/main/java/com/excelai/excel/MergeOptionsParser.java`
- Create: `backend/src/main/java/com/excelai/task/TaskReviewService.java`
- Modify: `backend/src/main/java/com/excelai/excel/ExcelWorkflowTools.java`
- Modify: `backend/src/main/java/com/excelai/task/TaskController.java`
- Test: `backend/src/test/java/com/excelai/task/TaskReviewServiceTest.java`
- Test: `backend/src/test/java/com/excelai/task/TaskControllerTest.java`

**Interfaces / 接口：**

- `MergeOptionsParser.parse(Map<String,Object>) -> MergeOptions` centralizes the existing option conversion used by the Agent tool and task review service.
- `TaskReviewService.getReview(Long taskId, Long userId, int page, int size) -> ReviewPage` validates ownership/status/workflow and returns at most 50 grouped rows per page, including source values, computed original output values, supported issues, saved actions, and original/revised file IDs.
- `TaskReviewService.submitReview(Long taskId, Long userId, ReviewRequest request) -> ReviewPage` accepts a full decision snapshot with `expectedVersion` and grouped row decisions.
- Add `GET /api/tasks/{id}/review` in Task 2 and define the submission DTO; wire `PUT` only after Task 3 has safe conditional persistence. Obtain `userId` only from `Authentication`.

- [x] Add tests for owner/status/workflow gates, grouped issue identity, pagination, and rows missing an original output counterpart. / 测试源码已添加并通过。
- [x] Extract the merge option parser without changing existing Agent tool behavior. / 已完成
- [x] Read only task-owned source files and selected worksheets; recompute row-level original output through `ExcelWorkflowService` rather than persisting source cells. / 已完成
- [x] Validate submitted row identities against the task's generated issue set; return conflict for unknown or stale review versions. / 已完成

### Task 3: Conditional persistence and revised result export / 任务 3：条件持久化与修订版导出

**Files / 文件：**

- Modify: `backend/src/main/java/com/excelai/task/TaskRepository.java`
- Modify: `backend/src/main/java/com/excelai/task/TaskReviewService.java`
- Modify: `backend/src/main/java/com/excelai/task/TaskController.java`
- Test: `backend/src/test/java/com/excelai/task/TaskRepositoryTest.java`
- Test: `backend/src/test/java/com/excelai/task/TaskReviewServiceTest.java`

**Interfaces / 接口：**

- Add `updateSummaryIfUnchanged(Long taskId, String expectedSummaryJson, String updatedSummaryJson) -> boolean`; update only a completed task whose stored summary still equals the expected value.
- `ReviewRequest` contains `expectedVersion` and the complete set of row decisions; review version starts at `0` and increments after a new successful revision.
- Store version, decision map, request digest, revised file ID, and timestamp under `resultSummary.review`; preserve all existing summary fields and `resultFileId`.

- [x] Add repository tests for successful compare-and-set and stale-summary rejection. / 测试源码已添加并通过。
- [x] Add service tests for valid/invalid corrections, keep/skip output, same-payload retry idempotence, concurrent update rejection, and export failure cleanup. / 测试源码已添加并通过。
- [x] Generate a uniquely named CSV/XLSX revised output using the original task's selected format and existing output services. / 已完成
- [x] Save the new file record, then conditionally update the review summary; if the conditional update fails, delete the unlinked file and return a conflict. / 已完成
- [x] If the expected version and request digest match the latest saved review, return the existing revision without creating another file. / 已完成

### Task 4: Task history review interface / 任务 4：任务历史复核界面

**Files / 文件：**

- Create: `backend/src/main/resources/static/row-review.mjs`
- Create: `backend/row-review.test.mjs`
- Modify: `backend/src/main/resources/static/index.html`

**Interfaces / 接口：**

- `row-review.mjs` groups and validates row decisions for the client; it must not decide server authority or transform workbook values.
- The page requests the review endpoint only after a user opens an eligible history task, renders cells with `textContent`, and submits a complete decision snapshot with the current version.

- [x] Add pure module tests for grouping, action validation, and restoration of saved decisions. / 测试源码已添加并通过。
- [x] Add bilingual controls for correct/keep/skip, corrected date/amount inputs, original/revised download links, paging, version conflicts, and safe empty/error states. / 已完成
- [x] Keep the original task result download available; refresh review state after a successful revision. / 已完成

### Task 5: Roadmap record and static review / 任务 5：路线图记录与静态自查

**Files / 文件：**

- Modify: `docs/superpowers/plans/2026-10-19-project-status-and-roadmap.md`
- Modify: this plan's checkboxes and status after implementation.

- [x] Add a bilingual dated entry recording implementation, test execution deferred by the user, known limits, and next step. / 已完成
- [x] Review every changed document and comment for equivalent Chinese and English content. / 已完成
- [x] Run `git diff --check` and inspect status/diff; do not run backend or Node tests until the user asks. / 已完成；测试仍按要求暂缓

## Verification Results / 验证结果

The user authorized the following focused checks on 2026-10-20; all passed. / 用户于 2026-10-20 授权运行以下定向检查，均已通过：

- `mvn -f backend/pom.xml -Dtest=ExcelWorkflowServiceTest,TaskReviewServiceTest,TaskControllerTest,TaskRepositoryTest test`
- `node --test backend/row-review.test.mjs`
- `node --check backend/src/main/resources/static/row-review.mjs`
- Extract the inline module from `backend/src/main/resources/static/index.html` and run `node --check` on it.

Backend: 34 tests passed with zero failures or errors; frontend module: 3 tests passed; both JavaScript syntax checks passed. Live UI acceptance remains open. / 后端 34 项测试通过，失败和错误均为 0；前端模块 3 项测试通过；两项 JavaScript 语法检查均通过。真实界面验收仍待完成。

## Self-Review / 自查

- **Coverage / 覆盖：** The tasks implement all spec acceptance criteria: scoped reading/submission, row grouping, deterministic edits, keep/skip, revised output, original-result retention, optimistic concurrency, safe rendering, and failure cleanup. / 各任务覆盖规格验收项：权限限制、异常分组、确定性修正、保留/跳过、修订版导出、原结果保留、并发控制、安全展示及失败清理。
- **Interfaces / 接口一致性：** The same `SourceRowKey`, `Decision`, `ReviewRequest.expectedVersion`, `resultSummary.review`, and existing `resultFileId` are used consistently across workflow, service, persistence, and UI tasks. / `SourceRowKey`、`Decision`、`ReviewRequest.expectedVersion`、`resultSummary.review` 与现有 `resultFileId` 在工作流、服务、持久化和界面任务中保持一致。
- **Review focus / 审查重点：** Test source is partial in several areas listed above; test execution remains deferred as the user requested. / 上述多处高风险场景的测试源码仍不完整；按用户要求暂缓执行测试。
- **Scope / 范围：** No other workflow, schema migration, approval subsystem, or dependency is included. / 不包含其他工作流、数据库迁移、审批子系统或新增依赖。
