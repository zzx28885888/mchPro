# Excel workflow API / Excel 工作流 API

This guide describes the typed task endpoints. Authenticate with the normal user JWT. Upload each workbook through `/api/files/upload` first; use the returned `fileId` values in order.

本指南说明结构化任务 API。请使用普通用户 JWT 鉴权。先通过 `/api/files/upload` 上传工作簿，再按顺序使用返回的 `fileId`。

## Preview / 预览

`POST /api/tasks/preview` accepts `fileIds`, `workflowType`, `options`, and optional `prompt`. Preview returns workbook names/sheets/headers, an operation summary, and a short-lived `previewFingerprint`. The UI should obtain the input structure with empty options, collect the user's choices, then request a final preview.

`POST /api/tasks/preview` 接收 `fileIds`、`workflowType`、`options` 和可选的 `prompt`。响应包含文件及工作表/表头信息、操作摘要和短期有效的 `previewFingerprint`。界面可先用空选项读取结构，再收集用户配置并请求最终预览。

```json
{
  "fileIds": ["file-a", "file-b"],
  "workflowType": "MERGE_CLEAN",
  "options": {
    "sheetNames": ["Sales", "Sales"],
    "headerAliases": {"amount": ["Amount", "销售额"]},
    "duplicateKeyColumns": ["order_id"],
    "skipBlankRows": true,
    "dateFormats": {"date": "yyyy-MM-dd"},
    "amountColumns": ["amount"]
  },
  "prompt": "合并销售表 / Merge sales sheets"
}
```

`RECONCILE` uses two file IDs and options `leftSheetName`, `rightSheetName`, `leftKeyColumns`, `rightKeyColumns`, optional `leftValueColumns`/`rightValueColumns`, and `tolerance`. `SUMMARY` uses one file ID and `sheetName`, `measureColumn`, `dimensionColumns`, `aggregation` (`SUM` or `COUNT`), and optional `dateColumn`, `datePeriod`, and `dateFormat`.

`RECONCILE` 使用两个文件 ID，选项包括 `leftSheetName`、`rightSheetName`、`leftKeyColumns`、`rightKeyColumns`、可选的 `leftValueColumns`/`rightValueColumns` 和 `tolerance`。`SUMMARY` 使用一个文件 ID，选项包括 `sheetName`、`measureColumn`、`dimensionColumns`、`aggregation`（`SUM` 或 `COUNT`），以及可选的 `dateColumn`、`datePeriod`、`dateFormat`。

## Confirm and retrieve / 确认并读取结果

After user confirmation, `POST /api/tasks` with the same request plus `previewFingerprint`. Structured tasks require exact input order and a current preview fingerprint. The legacy freeform contract remains `{ "fileId": "…", "prompt": "…" }`.

用户确认后，向 `POST /api/tasks` 提交同一请求并附加 `previewFingerprint`。结构化任务必须使用完全相同的文件顺序和有效预览指纹。旧版自由任务仍使用 `{ "fileId": "…", "prompt": "…" }`。

Poll `GET /api/tasks/{taskId}`. On completion, `resultSummary` contains the workflow type, operation definition, output sheet counts, category counts, warnings, and full row-level exceptions. The output file is downloaded through the normal owned-file download endpoint.

轮询 `GET /api/tasks/{taskId}`。任务完成后，`resultSummary` 包含工作流类型、操作定义、输出工作表行数、分类计数、警告和完整逐行异常。结果文件通过常规的用户归属下载接口获取。

The database migration `infra/migrations/001_excel_workflows.sql` adds the workflow fields and plan grants for existing databases. It is idempotent; apply it after the base schema has created the task and plan-permission tables.

数据库迁移 `infra/migrations/001_excel_workflows.sql` 为现有数据库增加工作流字段和套餐工具授权。该迁移可重复执行；请在基础 schema 创建任务表和套餐权限表后应用。
