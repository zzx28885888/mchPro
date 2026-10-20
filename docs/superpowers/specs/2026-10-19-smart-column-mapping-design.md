# 智能列映射与格式变化提醒设计 / Smart Column Mapping and Schema Drift Design

**日期 / Date:** 2026-10-19
**状态 / Status:** 设计已确认 / Design approved

## 目标 / Goal

让用户复用历史工作流规则处理新一批 Excel/CSV 文件时，系统能发现表头变化，并以可解释、可确认的方式建议列映射，减少重复配置和误处理。
Help users reuse historical workflow rules on new Excel/CSV files by detecting header changes and suggesting explainable, confirmable column mappings, reducing repeated setup and accidental misprocessing.

## 当前情况 / Current State

内置工作流模板通过静态中英别名为部分常见字段提供唯一匹配建议；任务历史保存工作流参数并支持复用规则。但任务历史没有保存每个输入工作表的完整表头，因此无法报告相对于上次运行新增或缺失的列。
Built-in workflow templates use static Chinese/English aliases to suggest unique matches for common fields, and task history stores workflow options for reuse. History does not retain complete headers for each input worksheet, so it cannot report columns added or missing since the previous run.

## 用户流程 / User Flow

1. 用户选择模板或复用历史任务规则，上传新文件并读取工作表结构。
   The user selects a template or reuses historical task rules, uploads new files, and inspects worksheet schemas.
2. 系统将新表头与历史任务保存的来源表头快照按输入顺序和工作表对比，显示缺失列、新增列和可解释的映射候选。
   The system compares new headers with the historical source-header snapshot by input order and worksheet, then shows missing columns, new columns, and explainable mapping candidates.
3. 系统只对唯一的规范化表头或已知别名匹配自动预填；歧义匹配只作为候选展示，用户确认或编辑后再生成执行预览。
   The system auto-fills only unique normalized-header or known-alias matches; ambiguous matches remain suggestions for the user to confirm or edit before building the run preview.
4. 变化提示不阻止任务。用户仍需通过既有预览指纹确认本次参数。
   Schema warnings do not block execution. The user still confirms the current options through the existing preview fingerprint flow.

## 设计 / Design

### 表头快照与预览指纹 / Header Snapshot and Preview Fingerprint

- 后端在配置完成的任务预览中读取所选工作表的完整表头，并生成版本化、按输入文件顺序排列的来源快照；快照包含输入位置、工作表名称和原始表头，不包含单元格数据。所选模板 ID 也保存到工作流选项中。
  During a configured task preview, the backend reads complete headers for selected worksheets and creates a versioned, input-ordered source snapshot containing input position, worksheet names, and original headers, but no cell data. The selected template ID is also stored in workflow options.
- 服务端生成快照并返回给前端；前端在确认请求中原样回传。服务端将模板 ID 与快照纳入既有预览摘要校验，并与工作流参数一起保存到现有 JSON 任务选项中。修改快照或参数必须导致指纹校验失败。
  The backend generates and returns the snapshot for the frontend to echo in the confirmation request. The backend includes the template ID and snapshot in the existing preview digest check and stores them with workflow options in the existing JSON field. Changing the snapshot or options must fail fingerprint validation.
- 执行工作流时，模板 ID 与快照只作为复用和审计元数据，不传入 Excel 操作工具，也不影响结果计算。无需数据库迁移。
  During workflow execution, the template ID and snapshot are reuse/audit metadata only; they are excluded from Excel operation tools and cannot affect result calculations. No database migration is required.

### 变化检测与映射建议 / Drift Detection and Mapping Suggestions

- 比较同一输入位置、同一工作表中的旧表头与新表头，并把变化分为“缺失列”“新增列”和“可能改名”。工作表重命名单独报告。
  Compare old and new headers at the same input position and worksheet. Classify differences as missing columns, added columns, or possible renames; report worksheet renames separately.
- 表头规范化使用 Unicode NFKC，忽略大小写、首尾空白和常见分隔标点；完全规范化匹配优先。模板现有别名用于补充已知中英文同义列。仅当旧表头和新表头唯一映射到同一已知别名组时，显示“可能改名”候选；不使用模型或模糊文本相似度猜测。
  Header normalization uses Unicode NFKC and ignores case, surrounding whitespace, and common separator punctuation; exact normalized matches take priority. Existing template aliases supplement known Chinese/English synonyms. A “possible rename” is suggested only when old and new headers uniquely map to the same known alias group; no model or fuzzy text-similarity guess is used.
- 映射目标必须唯一。无法唯一决定的候选只显示给用户，不自动套用；没有可靠候选时明确标记为待映射。
  A mapping target must be unique. Ambiguous candidates are shown without being applied, and columns without a reliable candidate are marked for mapping.
- 快照比较和候选建议仅展示列名、文件名、工作表名及变化类型，不展示任何单元格值；提示不会自动改写映射或阻止用户继续。
  Snapshot comparison and suggestions show only headers, filenames, worksheet names, and change types; no cell values are exposed, and warnings neither rewrite mappings nor block continuation.

### 数据契约与兼容性 / Data Contract and Compatibility

- 为既有 workflow options JSON 增加 `workflowTemplateId` 和版本化的 `schemaSnapshot` 元数据，避免数据库迁移；历史任务缺少这些字段时继续允许复用规则，只是不恢复模板别名或显示跨批次表头差异。
  Add `workflowTemplateId` and versioned `schemaSnapshot` metadata to the existing workflow-options JSON, avoiding a database migration. Older tasks without these fields can still reuse rules but cannot restore template aliases or show cross-run header differences.
- 预览与任务创建必须对同一快照和工作流选项做摘要校验，保持现有防篡改和过期预览行为。
  Preview and task creation must digest the same snapshot and workflow options, preserving existing tamper detection and preview expiration behavior.
- 自由模式保持现有请求流程；本功能用于有类型的工作流和通过历史任务复用规则的流程。
  Freeform mode keeps its existing request flow; this feature applies to typed workflows and historical-rule reuse.

## 非目标 / Non-Goals

- 不创建独立命名配置档或用户级映射数据库。
  Do not create a separate named-profile or user-level mapping database.
- 不自动修复数据、不改变单元格值，也不基于模型猜测来自动选择有歧义的列。
  Do not automatically repair data, change cell values, or use model guesses to resolve ambiguous column mappings.
- 不增加云盘、数据库、Webhook 或定时数据源连接。
  Do not add cloud-drive, database, webhook, or scheduled data-source integrations.

## 验收标准 / Acceptance Criteria

1. 新任务确认后，完整来源表头快照保存在任务历史选项中，且不包含单元格值。
   After a new task is confirmed, its complete source-header snapshot is stored in task-history options and contains no cell values.
2. 用户复用该任务规则并上传新文件时，预览能正确列出新增、缺失、重命名工作表和可能改名的列。
   When the user reuses the task rules with new files, the preview correctly lists added/missing columns, renamed worksheets, and possible column renames.
3. 唯一规范化匹配或唯一别名匹配可预填；多个候选不会自动映射，用户可编辑后继续。
   Unique normalized or alias matches can be prefilled; multiple candidates are never mapped automatically, and the user can edit before continuing.
4. 快照或规则被修改时，既有预览指纹校验拒绝任务创建；历史旧任务没有快照时仍可复用。
   Existing preview-fingerprint validation rejects task creation if the snapshot or rules are modified; older history entries without snapshots remain reusable.
5. 后端和前端测试源码覆盖快照生成/存储、指纹绑定、变化检测、歧义处理和旧历史兼容。
   Backend and frontend test sources cover snapshot generation/storage, fingerprint binding, drift detection, ambiguity handling, and compatibility with older history.

## 风险与边界 / Risks and Boundaries

- 仅依赖表头无法证明两个字段语义相同。任何不唯一的候选都必须留给用户确认。
  Headers alone cannot prove that two fields have the same meaning. Any non-unique candidate must remain for user confirmation.
- 多文件工作流依赖输入顺序保持一致；实现必须用输入位置和工作表身份比较，不能按文件名做唯一身份判断。
  Multi-file workflows depend on stable input order; comparison must use input position and worksheet identity rather than treating filenames as unique identities.
- 历史任务将增加少量表头元数据；不保存行数据或样例单元格值。
  Historical tasks will store a small amount of additional header metadata, but no row data or sample cell values.

## 验证约定 / Verification Convention

按用户此前要求，当前阶段可以编写测试源码和实施代码，但暂不运行测试；在声称验证通过前，必须另行运行计划中的测试与前端检查。
At the user's earlier request, implementation and test source may be written now, but tests are not to be run yet. The planned tests and frontend checks must be run before claiming verification is complete.
