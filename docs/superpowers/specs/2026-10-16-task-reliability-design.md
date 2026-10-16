# 任务可靠性设计 / Task Reliability Design

## 目标 / Goal

确保任务记录与月度额度在同一 PostgreSQL 事务中提交，并让持久化队列在后端重启后继续派发。确保 Python 到 Java 工具调用的读取超时大于 Java 工具最长执行时间。

Commit task records and monthly quota usage in one PostgreSQL transaction, resume dispatching durable queued work after backend restarts, and set Python-to-Java read timeouts above the longest Java tool execution time.

## 设计 / Design

1. **原子提交 / Atomic acceptance.** 使用 PostgreSQL 用户行锁串行化同一用户的月度额度检查；在单个 Spring 事务中检查额度、创建任务、写入任务文件关联和 usage_record。Redis 不再作为额度事实来源，避免 Redis 与 PostgreSQL 计数漂移。超额或任一步骤失败时，整个事务回滚。

   Serialize monthly quota checks per user with a PostgreSQL row lock. Check quota and insert the task, task-file links, and `usage_record` in one Spring transaction. PostgreSQL becomes the sole quota source of truth; Redis is removed from quota accounting. Quota rejection or any failed write rolls back the transaction.

2. **持久派发 / Durable dispatch.** 使用定时调度器从 PostgreSQL 读取 QUEUED 任务，通过条件状态更新抢占任务，再提交到现有有界线程池。线程池拒绝时恢复 QUEUED，供下一轮重试。应用启动时将残留 RUNNING 任务重置为 QUEUED；只在启动时执行，依赖当前 Compose 单实例部署。

   A scheduled dispatcher reads QUEUED jobs from PostgreSQL, claims each with a conditional status update, and submits it to the existing bounded executor. If the executor rejects submission, restore QUEUED for a later retry. On application startup, reset leftover RUNNING jobs to QUEUED; this recovery assumes the current single-instance Compose deployment.

3. **超时预算 / Timeout budget.** Python Java Tool Registry 客户端的默认读取超时设为 75 秒，大于 Java 当前最长 60 秒工具超时；保留环境变量覆盖能力，并在 README 说明上下游超时预算关系。

   Set the Python Java Tool Registry client's default read timeout to 75 seconds, above the current 60-second maximum Java tool timeout. Keep the environment override and document the upstream/downstream timeout budget.

## 验收标准 / Acceptance Criteria

- 任务和额度记录同事务提交；月度额度不超发，同一任务不会重复扣额。
- 超额拒绝或数据库写入失败时，不留下任务、任务文件关联或额度记录。
- 队列满时任务保持 QUEUED，并在后续调度轮次可被处理。
- 重启后残留 QUEUED/RUNNING 任务重新进入派发流程。
- 抢占状态更新阻止同一进程内重复派发同一任务。
- Python 工具客户端默认读取超时为 75 秒，且文档与配置保持一致。
- 本次文档及新增/修改注释均提供中英双语。

- Task and usage records commit together; monthly quotas cannot be exceeded and a task cannot be charged twice.
- Quota rejection or database write failure leaves no task, task-file link, or usage record behind.
- A full worker queue leaves the task QUEUED for a later dispatch attempt.
- Leftover QUEUED/RUNNING tasks resume dispatch after restart.
- Conditional claims prevent the same task from being dispatched twice within a process.
- The Python tool client's default read timeout is 75 seconds, with matching documentation and configuration.
- All documentation and added/modified comments in this batch are bilingual.

## 约束 / Constraints

- 不增加运行时服务或第三方依赖。 / Add no runtime services or third-party dependencies.
- 不新增或运行自动化测试；执行静态代码审查、XML/YAML/配置检查和 `git diff --check`。 / Do not add or run automated tests; use static code review, XML/YAML/configuration checks, and `git diff --check`.
- 启动时重置 RUNNING 的恢复策略只适用于当前单实例部署。 / Startup recovery of RUNNING jobs is limited to the current single-instance deployment.
