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