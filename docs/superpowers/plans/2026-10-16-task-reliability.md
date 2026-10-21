# 任务可靠性实施计划 / Task Reliability Implementation Plan

> **执行方式 / Execution:** 按已授权的内联方式由 Codex 顺序实施。 / Execution: Codex implements tasks inline in sequence, as already authorized.

**目标 / Goal:** 将任务与额度原子提交、从数据库恢复异步派发，并对齐 Python→Java 超时预算。 / Atomically commit tasks and quota usage, recover asynchronous dispatch from PostgreSQL, and align the Python-to-Java timeout budget.

**架构 / Architecture:** PostgreSQL 是任务队列与额度的唯一持久化事实来源；Spring 定时器条件抢占 QUEUED 任务并交给现有有界执行器。Python 工具 HTTP 读取超时高于 Java 工具最长执行时间。 / PostgreSQL is the durable source of truth for tasks and quota; a Spring scheduler conditionally claims QUEUED tasks and submits them to the existing bounded executor. Python's tool HTTP read timeout exceeds the longest Java tool execution time.

**技术栈 / Tech Stack:** Spring Boot scheduling/transactions, PostgreSQL, Spring `ThreadPoolTaskExecutor`, Python httpx. / Spring Boot 调度与事务、PostgreSQL、Spring `ThreadPoolTaskExecutor`、Python httpx。

**规格 / Spec:** [任务可靠性设计 / Task Reliability Design](../specs/2026-10-16-task-reliability-design.md)

## 全局约束 / Global Constraints

- 文档和新增/修改注释必须中英双语。 / Documentation and added/modified comments must be bilingual.
- 不新增运行时服务或第三方依赖。 / Add no runtime services or third-party dependencies.
- 不新增或运行自动化测试；仅做静态审查与格式/配置检查。 / Do not add or run automated tests; use static review and format/configuration checks only.
- RUNNING 任务启动恢复针对当前单实例 Compose 部署。 / RUNNING-task startup recovery targets the current single-instance Compose deployment.

## 评审重点 / Review Focus

- 并发额度提交不得使同一用户超过月度配额。 / Concurrent submissions must not exceed a user's monthly quota.
- 数据库写入失败必须回滚任务与 usage_record。 / Database failures must roll back both task and usage records.
- 线程池拒绝任务后不能留下不可派发的 RUNNING 状态。 / Executor rejection must not strand a task in RUNNING.
- 状态抢占必须阻止重复执行，且终态不能被恢复逻辑覆盖。 / Claims must prevent duplicate execution, and recovery must not overwrite terminal states.
- Python 读取超时必须大于工具执行超时，并避免不必要地增加连接超时。 / Python read timeout must exceed tool execution time without unnecessarily extending connect timeout.

---

### Task 1: Atomic Task and Quota Acceptance / 任务 1：任务和额度原子提交

**Files / 文件：**
- Modify / 修改: `backend/src/main/java/com/excelai/usage/UsageService.java`
- Modify / 修改: `backend/src/main/java/com/excelai/task/TaskRepository.java`
- Modify / 修改: `backend/src/main/java/com/excelai/task/TaskService.java`

- [x] Move quota enforcement to PostgreSQL: lock the user's row, count current-month TASK records, reject if the plan limit is reached, and insert the unique usage record in the same transaction as task and input-link creation. / 将额度判断迁移到 PostgreSQL：锁定用户行、统计当月 TASK 记录、超额拒绝，并在任务及输入关联创建的同一事务中插入唯一 usage 记录。
- [x] Remove Redis quota-key seeding/increment/decrement; ensure quota failures roll back all task writes and no longer mark a rejected task FAILED. / 删除 Redis 额度键回填、增减逻辑；确保额度拒绝回滚全部任务写入，不再把被拒绝任务标记为 FAILED。
- [x] Review transaction boundaries and duplicate-task charging behavior; `git diff --check` passed. / 检查事务边界和重复任务扣额行为；`git diff --check` 已通过。

### Task 2: Durable Queue Dispatch and Recovery / 任务 2：持久队列派发与恢复

**Files / 文件：**
- Modify / 修改: `backend/src/main/java/com/excelai/task/TaskRepository.java`
- Modify / 修改: `backend/src/main/java/com/excelai/task/TaskWorker.java`
- Create / 新建: `backend/src/main/java/com/excelai/task/TaskDispatcher.java`
- Modify / 修改: `backend/src/main/java/com/excelai/config/AppConfig.java`
- Modify / 修改: `backend/src/main/java/com/excelai/task/TaskService.java`

- [x] Add ordered queued-task lookup and conditional QUEUED→RUNNING claim; startup resets only leftover RUNNING jobs to QUEUED for the documented single-instance deployment. / 增加按序查询排队任务和 QUEUED→RUNNING 条件抢占；启动时仅将残留 RUNNING 任务重置为 QUEUED，适用于文档说明的单实例部署。
- [x] Add a scheduled dispatcher that uses the existing bounded executor, reloads trusted task data from PostgreSQL, and requeues tasks if executor submission is rejected. / 新增定时派发器，使用现有有界线程池，从 PostgreSQL 重新读取可信任务数据；线程池拒绝时将任务恢复为 QUEUED。
- [x] Remove request-time in-memory dispatch; ensure only successfully claimed jobs execute and task failures are persisted. / 移除请求时的进程内直接派发；确保仅执行成功抢占的任务并持久化失败状态。
- [x] Review crash/rejection/state-transition paths; `git diff --check` passed. / 检查崩溃、拒绝和状态转换路径；`git diff --check` 已通过。

### Task 3: Align Tool Request Timeout / 任务 3：对齐工具请求超时

**Files / 文件：**
- Modify / 修改: `agent-service/java_tool_client.py`
- Modify / 修改: `.env.example`
- Modify / 修改: `README.md`

- [x] Use an HTTPX timeout with a 75-second read limit and a shorter explicit connect limit; retain `JAVA_TOOL_HTTP_TIMEOUT` override compatibility. / 配置 HTTPX 读取超时 75 秒、连接超时较短，并兼容现有 `JAVA_TOOL_HTTP_TIMEOUT` 覆盖方式。
- [x] Document the 60-second Java tool maximum and 75-second Python read-timeout buffer in Chinese and English. / 以中英双语说明 Java 工具最长 60 秒和 Python 读取超时 75 秒的缓冲关系。
- [x] Check changed comments/documentation; Python syntax parsing and `git diff --check` passed. / 检查本次修改的注释/文档；Python 语法解析和 `git diff --check` 已通过。
