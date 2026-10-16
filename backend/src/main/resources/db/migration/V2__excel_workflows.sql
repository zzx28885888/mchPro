-- 中文：为异步任务增加工作流元数据和有序多输入文件关联。
-- English: Add workflow metadata and ordered multi-input links for asynchronous tasks.
ALTER TABLE ai_task ADD COLUMN IF NOT EXISTS workflow_type VARCHAR(32) NOT NULL DEFAULT 'FREEFORM';
ALTER TABLE ai_task ADD COLUMN IF NOT EXISTS workflow_options TEXT NOT NULL DEFAULT '{}';
ALTER TABLE ai_task ADD COLUMN IF NOT EXISTS result_summary TEXT;

CREATE TABLE IF NOT EXISTS ai_task_input (
    task_id BIGINT NOT NULL REFERENCES ai_task(id) ON DELETE CASCADE,
    file_id BIGINT NOT NULL REFERENCES user_file(id),
    ordinal INTEGER NOT NULL CHECK (ordinal >= 0),
    PRIMARY KEY (task_id, file_id),
    UNIQUE (task_id, ordinal)
);

INSERT INTO ai_task_input(task_id, file_id, ordinal)
SELECT id, input_file_id, 0 FROM ai_task
WHERE input_file_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM ai_task_input i WHERE i.task_id=ai_task.id AND i.file_id=ai_task.input_file_id);
-- 中文：为所有现有套餐登记第一阶段工作流工具许可，尊重管理员已有配置。
-- English: Register phase-one workflow tools for existing plans while preserving administrator overrides.
INSERT INTO plan_tool_permission(plan_code, tool_name, enabled)
SELECT p.plan_code, t.tool_name, TRUE
FROM plan p
CROSS JOIN (VALUES ('inspect_workflow_inputs'), ('merge_clean_workbooks'), ('reconcile_workbooks'), ('summarize_workbook'), ('export_workbook_result')) AS t(tool_name)
ON CONFLICT (plan_code, tool_name) DO NOTHING;
