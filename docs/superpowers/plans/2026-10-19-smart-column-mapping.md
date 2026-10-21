# 智能列映射与格式变化提醒实施计划 / Smart Column Mapping and Schema Drift Implementation Plan

**目标 / Goal:** 用户复用模板或历史工作流处理新文件时，能看到基于历史表头的变化提醒和安全列映射建议；快照由服务端生成并绑定预览指纹。 / When users reuse a template or historical workflow on new files, show historical-header drift warnings and safe mapping suggestions; generate snapshots on the server and bind them to the preview fingerprint.

**架构 / Architecture:** 在现有任务选项 JSON 中保存模板 ID 和版本化表头快照；TaskService 在配置预览时生成快照并将其纳入现有请求摘要；TaskWorker 执行前剥离仅供历史复用的元数据；前端模板模块负责保守匹配和历史差异计算，任务历史继续通过现有接口提供数据。无需数据库迁移。 / Store the template ID and versioned header snapshot in the existing task-options JSON; TaskService generates snapshots during configured previews and includes them in the existing request digest; TaskWorker strips reuse-only metadata before execution; the frontend template module handles conservative matching and historical comparison, with history data coming through the existing API. No database migration is needed.

**技术栈 / Tech Stack:** Java 21, Spring Boot, Jackson, existing Redis preview fingerprints, static browser JavaScript modules, JUnit 5/Mockito, Node test runner. / Java 21, Spring Boot, Jackson, existing Redis preview fingerprints, static browser JavaScript modules, JUnit 5/Mockito, and the Node test runner.

**规格 / Specification:** [智能列映射与格式变化提醒设计 / Smart Column Mapping and Schema Drift Design](../specs/2026-10-19-smart-column-mapping-design.md)

**全局约束 / Global Constraints:**

- 修改的文档与注释须中英双语；不翻译无关旧内容。 / Changed documentation and comments must be bilingual; do not translate unrelated legacy content.
- 不存储单元格值；快照只包含输入位置、工作表名称和原始表头。 / Do not store cell values; snapshots contain only input positions, worksheet names, and original headers.
- 仅自动应用唯一规范化表头或已知别名匹配；歧义仅展示候选，不用模糊相似度或模型猜测。 / Auto-apply only unique normalized-header or known-alias matches; show ambiguous candidates without applying them, and do not use fuzzy similarity or model guesses.
- 表头警告不阻止任务；自由模式保持现有流程；旧历史无快照时仍可复用规则。 / Header warnings do not block tasks; freeform keeps its current flow; legacy history without snapshots remains reusable.
- 按用户要求，本阶段不运行测试或手动验收；实现完成前不宣称验证通过。 / Per the user's request, do not run tests or manual acceptance in this phase; do not claim verification passed before it is performed.

**审查重点 / Review Focus:** 服务端生成的快照是否被预览指纹完整保护；历史数据是否严格按输入位置和工作表比较；歧义字段是否始终需要用户确认；元数据是否不会泄漏到 Excel 工具参数。 / Check that server-generated snapshots are fully protected by the preview fingerprint; history comparison uses input position and worksheet identity; ambiguous fields always require user confirmation; and metadata never reaches Excel tool arguments.

## 文件与接口约定 / File and Interface Map

- `backend/src/main/java/com/excelai/task/TaskService.java`: preview adds `workflowTemplateId` and `schemaSnapshot` to a server-owned options copy. Preview response exposes `confirmedOptions` so the browser can echo the exact digest-bound options into create. Snapshot contract: `{version: 1, inputs: [{inputIndex, sheets: [{name, headers}]}]}`. Create normalizes and digests the echoed options, preserving current Redis expiration and tamper checks.
- `backend/src/main/java/com/excelai/task/TaskWorker.java`: remove `workflowTemplateId` and `schemaSnapshot` from a copied options map before calling `agent.execute`; persisted history retains the metadata.
- `backend/src/main/resources/static/workflow-templates.mjs`: export conservative header normalization, unique historical-header restoration, and a drift comparison helper. Drift result identifies added/missing columns, worksheet name changes, unique alias-based possible renames, and unresolved ambiguous candidates.
- `backend/src/main/resources/static/index.html`: include selected template ID in preview options; take server `confirmedOptions` for confirmation; restore template ID and snapshot from history; compare selected new-file schemas to the prior task snapshot and render bilingual non-blocking drift/mapping hints.
- `backend/src/test/java/com/excelai/task/TaskServiceTest.java`: cover server snapshot scope, options round trip, digest tampering, legacy compatibility, and absence of cell values.
- `backend/src/test/java/com/excelai/task/TaskWorkerTest.java` (create only if no suitable existing worker test): verify reuse metadata is removed before Agent execution while stored options remain untouched.
- `backend/workflow-templates.test.mjs`: cover NFKC/separator normalization, unique and ambiguous mapping, drift categories, alias rename, and missing/legacy snapshots.
- `docs/superpowers/plans/2026-10-19-project-status-and-roadmap.md`: add a bilingual dated delivery entry and update the next-step recommendation after implementation; record that verification remains deferred.

## 实施任务 / Implementation Tasks

### Task 1：先为快照和预览指纹补测试源码 / Task 1: Add test source for snapshots and preview fingerprints first

**修改文件 / Files:** `backend/src/test/java/com/excelai/task/TaskServiceTest.java`

1. 新增预览用例：选中工作表的快照按输入顺序返回，包含完整表头但不包含任何数据行或单元格值。 / Add a preview case proving selected-sheet snapshots preserve input order and include full headers but no data rows or cell values.
2. 新增创建用例：前端回传 `confirmedOptions` 后任务历史选项保存模板 ID 与相同快照；修改快照或模板 ID 后创建被指纹校验拒绝。 / Add a create case proving echoed `confirmedOptions` stores the same template ID and snapshot in task history; changing either must fail fingerprint validation.
3. 新增旧调用兼容用例：无模板 ID 的既有 typed workflow 仍可完成预览确认，FREEFORM 维持原请求。 / Add compatibility cases showing typed workflows without template IDs still preview/confirm and FREEFORM retains its existing request.
4. 先确认测试断言表达上述行为；按用户要求暂不执行 Maven 测试。 / Confirm the assertions express these behaviors; defer Maven execution per user request.

### Task 2：实现服务端快照生成与 digest-bound confirmed options / Task 2: Implement server snapshots and digest-bound confirmed options

**修改文件 / Files:** `backend/src/main/java/com/excelai/task/TaskService.java`, `backend/src/test/java/com/excelai/task/TaskServiceTest.java`

1. 为 typed workflow 从实际上传工作簿读取所选 sheet 名称及 headers，并构造版本 1 快照；工作簿读取继续复用当前预览检查逻辑，快照不复制行数据。 / For typed workflows, read selected sheet names and headers from the uploaded workbooks and build a version-1 snapshot; reuse current preview inspection and do not copy row data.
2. 将模板 ID 和快照合并到服务端生成的 options 副本，返回 `confirmedOptions`；以该选项副本生成 Redis 请求摘要，并在任务创建时对前端回传值执行相同规范化与摘要校验。 / Merge template ID and snapshot into a server-generated options copy, return it as `confirmedOptions`, digest that copy in Redis, and apply the same normalization/digest check to the echoed value during task creation.
3. 将确认后的选项写入现有 workflow-options JSON；限制 metadata 的接受位置和类型，防止任意客户端在无对应预览的情况下创建任务。 / Persist confirmed options in the existing workflow-options JSON; constrain metadata location and types so arbitrary client values cannot bypass preview confirmation.
4. 保持现有 `operationSummary.rules` 可读，避免把完整快照塞入用户配置摘要；确保旧历史没有快照时仍能解析。 / Keep `operationSummary.rules` readable and avoid embedding the full snapshot in the user-facing rule summary; ensure old history without snapshots remains parseable.

### Task 3：剥离执行期元数据 / Task 3: Strip execution-only metadata

**修改文件 / Files:** `backend/src/main/java/com/excelai/task/TaskWorker.java`, `backend/src/test/java/com/excelai/task/TaskWorkerTest.java` (if needed)

1. 从持久化选项构造新的执行选项副本，移除 `workflowTemplateId` 和 `schemaSnapshot` 后传给 `agent.execute`。 / Build a separate execution-options copy from persisted options and remove `workflowTemplateId` and `schemaSnapshot` before calling `agent.execute`.
2. 保留存储选项与历史读取值；通过测试源码确认 Agent 收到的业务规则不变且没有审计元数据。 / Preserve persisted/history options; add test source confirming the Agent receives unchanged business rules without audit metadata.

### Task 4：实现规范化映射与历史差异计算 / Task 4: Implement normalized mapping and historical drift comparison

**修改文件 / Files:** `backend/src/main/resources/static/workflow-templates.mjs`, `backend/workflow-templates.test.mjs`

1. 将表头规范化扩展为 NFKC、大小写折叠、首尾空格去除及指定常见分隔符处理；保留原始列名作为结果值。 / Extend header normalization with NFKC, case folding, surrounding-whitespace trimming, and defined common separator handling; preserve original names as returned values.
2. 暴露历史表头恢复能力：规范化匹配优先，只有唯一时才替换旧表头；否则仅在唯一已知别名匹配时恢复；多个匹配不得自动选取。 / Expose historical-header restoration: prefer normalized matches and replace old headers only when unique; otherwise restore only a unique known-alias match, never selecting among multiple matches.
3. 对齐同一 `inputIndex` 和 sheet 名称的快照，报告新增/缺失列；对 sheet 重命名单独给出候选；仅旧、新列唯一命中同一别名组时报告可能改名。 / Align snapshots by `inputIndex` and sheet name, report added/missing columns, report worksheet renames separately, and suggest a possible column rename only when old and new headers uniquely match the same alias group.
4. 为同名重复列、多个别名候选、输入顺序变化、缺失快照和旧版本快照添加测试源码断言。 / Add test-source assertions for duplicate header names, multiple alias candidates, changed input order, absent snapshots, and older snapshot versions.

### Task 5：接入模板历史恢复和非阻断提示 / Task 5: Integrate template history restoration and non-blocking hints

**修改文件 / Files:** `backend/src/main/resources/static/index.html`, `backend/workflow-templates.test.mjs`

1. 在构建预览请求时记录所选模板 ID；收到预览后用服务端 `confirmedOptions` 作为最终确认请求的 options，避免客户端重建快照。 / Include the selected template ID in preview options; after preview, use server `confirmedOptions` as the final create request's options so the client never rebuilds snapshots.
2. 从历史任务恢复模板 ID 和旧快照；按唯一规范化/别名匹配预填可安全恢复的历史字段；对旧记录无 snapshot 的情况不报错，继续沿用历史规则。 / Restore template ID and prior snapshot from history; prefill historical fields only through unique normalized/alias matches; handle records without snapshots gracefully and continue using their rules.
3. 选择新文件或工作表后运行差异计算，在工作流配置面板以中英双语展示新增/缺失/疑似改名和需要确认的候选，不显示单元格数据、不自动覆盖现有规则。 / After new files or sheets are selected, calculate drift and show bilingual added/missing/possible-rename and confirmation-needed hints in the workflow panel; do not show cell values or overwrite existing rules.
4. 检查内容使用 `textContent` 或现有安全渲染方式，并确保警告不禁用预览/确认按钮。 / Render content through `textContent` or existing safe rendering and ensure warnings do not disable preview/confirmation.

### Task 6：更新路线图并做静态差异自查 / Task 6: Update the roadmap and review the static diff

**修改文件 / Files:** `docs/superpowers/plans/2026-10-19-project-status-and-roadmap.md` and changed implementation files

1. 在路线图顶部添加双语交付记录，准确记载实现项、未运行的验证、限制和下一步。 / Add a bilingual delivery entry near the top of the roadmap covering implementation, deferred verification, limitations, and next steps.
2. 对照规格验收项和本计划核对文件路径、字段名、版本格式、旧数据兼容以及危险边界；检查新增/修改注释与文档的中英一致性。 / Check file paths, field names, version shape, legacy compatibility, and safety boundaries against the spec; review changed comments and documentation for bilingual equivalence.
3. 仅做静态 diff 检查；不运行测试或手动验收，除非用户之后明确要求。 / Perform only a static diff review; do not run tests or manual acceptance unless the user later explicitly requests it.

## 自我审查 / Self-Review

- **覆盖度 / Coverage:** 规格中的快照、指纹、历史兼容、唯一映射、歧义处理、差异类别、执行期隔离及安全展示均有对应任务。 / Snapshot generation, fingerprint binding, history compatibility, unique mapping, ambiguity handling, drift categories, execution isolation, and safe rendering all map to tasks.
- **粒度 / Granularity:** 每项任务可单独审查；接口先固定为 `confirmedOptions` 和 `{version, inputs}` 快照结构，再跨层接入。 / Tasks are independently reviewable; the `confirmedOptions` response and `{version, inputs}` snapshot shape are fixed before cross-layer integration.
- **一致性 / Consistency:** Task 2 creates and digests metadata; Task 5 echoes that exact object; Task 3 removes only metadata; history continues through the existing JSON field. / Task 2 creates and digests metadata; Task 5 echoes that exact object; Task 3 strips only metadata; history remains on the existing JSON field.
- **验证边界 / Verification Boundary:** Tests are specified as source changes but explicitly deferred from execution, matching the user's current instruction. / Tests are planned as source changes but explicitly deferred from execution, matching the user's current instruction.
