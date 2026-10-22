# LLM 用量采集与成本对比实施计划

# LLM Usage Metering and Cost Comparison Implementation Plan

> **执行者须知 / For agentic workers:** 按任务逐项执行本计划，并在实现每一项前阅读 `subagent-driven-development` 或 `executing-plans` 技能。步骤使用复选框跟踪。
>
> **For agentic workers:** Execute this plan task by task and read the `subagent-driven-development` or `executing-plans` skill before implementation. Steps use checkboxes for tracking.

**目标 / Goal：** 为每次表格任务采集并持久化 Agent 模型用量，再用虚构数据比较本地 Ollama Qwen3.5:9B 和远程 DeepSeek Flash。

**架构 / Architecture：** Python Agent 在每次模型响应后归并供应商用量和单调时钟耗时，向 Spring 返回回答及仅含计量字段的摘要。Spring 生成执行 UUID，将摘要存入独立 Flyway 表；任务成功状态不依赖用量存储。随后通过四类固定工作流各运行三次，生成带正确性、范围、中位数及远程官方费率的双语报告。

**Architecture:** The Python Agent aggregates provider usage and monotonic-clock duration after each model response, then returns the answer and a metering-only summary to Spring. Spring creates an execution UUID and persists the summary in a separate Flyway table; task success does not depend on metering storage. Finally, run four fixed workflow types three times each and produce a bilingual report with correctness, ranges, medians, and official remote rates.

**技术栈 / Tech Stack：** Python 3、LangGraph/LangChain、FastAPI、Java 21、Spring Boot、PostgreSQL、Flyway、JUnit/Mockito、pytest。

**规格 / Spec：** [2026-10-22-llm-usage-cost-metering-design.md](../specs/2026-10-22-llm-usage-cost-metering-design.md)

## 全局约束 / Global Constraints

- 每次 Agent 执行写一条记录，以唯一 `execution_id` 和任务 ID 关联；重试使用新执行 ID。
- Persist one row per Agent execution, linked by a unique `execution_id` and task ID; retries use a new execution ID.
- 用量字段为 `input_tokens`、`output_tokens`、`cached_input_tokens`、`model_call_count`、`llm_duration_ms`，并保存供应商、模型、状态和记录时间。
- Usage fields are `input_tokens`, `output_tokens`, `cached_input_tokens`, `model_call_count`, and `llm_duration_ms`, with provider, model, status, and record time.
- 测量状态只使用 `COMPLETE`、`PARTIAL`、`UNAVAILABLE`；未知值保持为 `NULL`，不得以零或 tokenizer 估算代替。
- Measurement status is `COMPLETE`, `PARTIAL`, or `UNAVAILABLE`; unknown values remain `NULL` and must not be replaced with zero or tokenizer estimates.
- 用量记录、响应和普通日志不得包含提示词、回答、工作簿内容、文件名、完整请求/响应或 API 密钥。
- Usage rows, responses, and ordinary logs must not contain prompts, answers, workbook contents, filenames, full requests/responses, or API keys.
- 用量写入失败是辅助测量故障，不得改变任务本身成功/失败状态。
- Metering persistence is auxiliary and its failure must not change the task's own success/failure state.
- 不改变现有 `usage_record` 套餐额度、任务 API、任务流程或套餐价格/次数。
- Do not change existing `usage_record` plan quotas, task APIs, task flow, or plan prices/limits.
- 远程路线为 DeepSeek Flash；密钥只由用户在私密本地环境中配置。
- The remote route is DeepSeek Flash; the user configures its key only in a private local environment.
- 对照实验使用虚构数据；每种工作流每种模型运行 3 次，共本地 12 次、远程 12 次。
- The comparison uses synthetic data; run each workflow three times per model, for 12 local and 12 remote runs.
- 所有本次新增或修改的维护文档及代码注释均须中英双语。
- All documentation and code comments added or changed in this work must be bilingual in Chinese and English.

## 评审重点 / Review Focus

- 部分模型轮次没有 token 计数时，状态和各字段是否能表示部分缺失；由任务 1 的聚合测试覆盖。
- When some model turns lack token counts, can status and fields represent partial absence? Covered by Task 1 aggregation tests.
- LangChain/Ollama/远程响应的用量元数据形状不一致时，是否正确映射且不误判为零；由任务 1 的适配测试覆盖。
- Do differing LangChain/Ollama/remote usage shapes map correctly without treating absence as zero? Covered by Task 1 adapter tests.
- Agent 工具循环中多轮模型响应是否全部计入，但工具结果和原始消息不进入 HTTP 响应；由任务 2 的路由测试覆盖。
- Are all model turns in the Agent tool loop counted while tool results and raw messages stay out of the HTTP response? Covered by Task 2 route tests.
- Agent 返回用量后结果文件校验失败时，已知用量是否仍落库；由任务 4 的 Worker 测试覆盖。
- If result-file validation fails after the Agent returns usage, is known usage still persisted? Covered by Task 4 worker tests.
- 计量数据库写入失败或 Agent 超时时，是否既记录缺失测量又保持原任务状态语义；由任务 4 的故障测试覆盖。
- On metering database failure or Agent timeout, is measurement loss recorded while preserving task-state semantics? Covered by Task 4 failure tests.

## 文件与职责 / File Map

- `agent-service/agent_graph.py`：模型调用计量与多轮汇总。
- `agent-service/agent_graph.py`: model-call measurement and multi-turn aggregation.
- `.env.example`、`agent-service/.env.example`：可选远程供应商标签 `REMOTE_LLM_PROVIDER`，默认 `openai-compatible`。
- `.env.example`, `agent-service/.env.example`: optional remote provider label `REMOTE_LLM_PROVIDER`, defaulting to `openai-compatible`.
- `docker-compose.yml`：将远程供应商标签传入 Python Agent 容器。
- `docker-compose.yml`: passes the remote provider label into the Python Agent container.
- `agent-service/app.py`：请求上下文初始化和精简 Agent 响应。
- `agent-service/app.py`: request-context initialization and minimal Agent response.
- `agent-service/tests/test_usage_metering.py`、`agent-service/tests/test_app.py`（若已有测试文件则扩展）：Python 聚合、适配和 HTTP 合约测试。
- `agent-service/tests/test_usage_metering.py` and `agent-service/tests/test_app.py` (extend the existing test file if present): Python aggregation, adapter, and HTTP-contract tests.
- `backend/src/main/java/com/excelai/agent/AgentService.java`：请求/响应 DTO、执行 ID 与 Agent 调用。
- `backend/src/main/java/com/excelai/agent/AgentService.java`: request/response DTOs, execution ID, and Agent call.
- `backend/src/main/java/com/excelai/task/LlmUsageRecord.java`、`LlmUsageRepository.java`（按现有 repository 组织方式放置）：计量实体与持久化接口。
- `backend/src/main/java/com/excelai/task/LlmUsageRecord.java` and `LlmUsageRepository.java` (place according to existing repository organization): metering entity and persistence interface.
- `backend/src/main/java/com/excelai/task/TaskWorker.java`：保存成功或缺失测量，不影响任务状态。
- `backend/src/main/java/com/excelai/task/TaskWorker.java`: persist available or unavailable measurements without changing task state.
- `backend/src/main/resources/db/migration/V3__llm_usage_metering.sql`：独立计量表和约束。
- `backend/src/main/resources/db/migration/V3__llm_usage_metering.sql`: separate metering table and constraints.
- `backend/src/test/java/com/excelai/agent/AgentServiceTest.java`、`backend/src/test/java/com/excelai/task/LlmUsageRepositoryTest.java`、`backend/src/test/java/com/excelai/task/TaskWorkerUsageTest.java`：Java DTO、repository 和任务故障语义测试。
- `backend/src/test/java/com/excelai/agent/AgentServiceTest.java`, `backend/src/test/java/com/excelai/task/LlmUsageRepositoryTest.java`, and `backend/src/test/java/com/excelai/task/TaskWorkerUsageTest.java`: Java DTO, repository, and task failure-semantics tests.
- `docs/superpowers/reports/2026-10-22-llm-usage-cost-comparison.md`：实际实验结果、官方费率快照引用和限制。
- `docs/superpowers/reports/2026-10-22-llm-usage-cost-comparison.md`: measured experiment results, official rate references, and limitations.

---

### Task 1: Python Usage Summary Aggregation / 任务 1：Python 用量摘要聚合

**文件 / Files：**

- 新建：`agent-service/usage_metering.py`
- Create: `agent-service/usage_metering.py`
- 修改：`agent-service/agent_graph.py`
- Modify: `agent-service/agent_graph.py`
- 测试：`agent-service/tests/test_usage_metering.py`
- Test: `agent-service/tests/test_usage_metering.py`

**接口 / Interfaces：**

- 提供不可变 `UsageSummary(provider_code: str, model_name: str, input_tokens: int | None, output_tokens: int | None, cached_input_tokens: int | None, model_call_count: int, llm_duration_ms: int, measurement_status: Literal["COMPLETE", "PARTIAL", "UNAVAILABLE"])`。
- Provide immutable `UsageSummary(provider_code: str, model_name: str, input_tokens: int | None, output_tokens: int | None, cached_input_tokens: int | None, model_call_count: int, llm_duration_ms: int, measurement_status: Literal["COMPLETE", "PARTIAL", "UNAVAILABLE"])`.
- 提供 `extract_turn_usage(response, provider_code: str, model_name: str) -> TurnUsage` 和 `aggregate_usage(turns: Sequence[TurnUsage]) -> UsageSummary`；字段缺失保留 `None`。
- Provide `extract_turn_usage(response, provider_code: str, model_name: str) -> TurnUsage` and `aggregate_usage(turns: Sequence[TurnUsage]) -> UsageSummary`; missing fields remain `None`.
- Agent 模型调用处使用 `time.perf_counter()`，逐响应记录 `TurnUsage`，覆盖工具循环的每个模型轮次。
- At Agent model invocation sites, use `time.perf_counter()` and record one `TurnUsage` per response, including each model turn in the tool loop.

- [x] **步骤 1：先写失败测试。** 测试完整多轮求和、一个轮次缺失用量时 `PARTIAL`、所有轮次无计数时 `UNAVAILABLE`、零计数保持为零、缓存计数为 `None` 时不参与伪造总数，并覆盖 LangChain `usage_metadata` 与 Ollama `prompt_eval_count`/`eval_count` 映射。
- [x] **Step 1: Write failing tests.** Cover complete multi-turn sums, `PARTIAL` when one turn lacks usage, `UNAVAILABLE` when all turns lack counts, preservation of measured zero, no invented cached count when absent, and mappings from LangChain `usage_metadata` and Ollama `prompt_eval_count`/`eval_count`.

- [x] **步骤 2：确认测试因缺少接口而失败。** 运行 `cd agent-service; pytest tests/test_usage_metering.py -q`；预期为接口不存在或断言失败。
- [x] **Step 2: Confirm tests fail for the missing interface.** Run `cd agent-service; pytest tests/test_usage_metering.py -q`; expect missing-interface or assertion failures.

- [x] **步骤 3：实现 `TurnUsage`、`UsageSummary`、用量提取与聚合。** 只读取响应中的计量字段；同一执行使用一致 provider/model，调用次数和耗时对所有轮次求和。
- [x] **Step 3: Implement `TurnUsage`, `UsageSummary`, extraction, and aggregation.** Read only metering fields; keep provider/model consistent within an execution and sum call count and duration across turns.

- [x] **步骤 4：在 Agent 图模型调用边界计时并逐轮收集用量。** 保证正常结束和模型异常路径都能保留已收集轮次；不记录提示词、回答或工具输出。
- [x] **Step 4: Measure and collect usage at the Agent graph model-call boundary.** Preserve already collected turns on normal completion and model-error paths; do not record prompts, answers, or tool outputs.

- [x] **步骤 5：重跑 Python 定向测试。** 运行 `cd agent-service; pytest tests/test_usage_metering.py -q`；预期全部通过。
- [x] **Step 5: Rerun targeted Python tests.** Run `cd agent-service; pytest tests/test_usage_metering.py -q`; expect all tests to pass.

### Task 2: Python Agent HTTP Contract / 任务 2：Python Agent HTTP 合约

**文件 / Files：**

- 修改：`agent-service/app.py`
- Modify: `agent-service/app.py`
- 修改或新建：`agent-service/tests/test_app.py`
- Modify or create: `agent-service/tests/test_app.py`

**接口 / Interfaces：**

- `/agent/chat` 请求新增必需 `execution_id`（UUID 字符串）和现有任务 ID；响应为 `{"answer": str, "usage": UsageSummary}`。
- `/agent/chat` request adds required `execution_id` (UUID string) and existing task ID; response is `{"answer": str, "usage": UsageSummary}`.
- 执行 ID 进入请求上下文供图执行使用；响应不得再包含 `messages`、工具结果或异常中的原始模型内容。
- Put the execution ID in request context for graph execution; the response must no longer include `messages`, tool results, or raw model content from exceptions.

- [x] **步骤 1：先写 HTTP 路由测试。** 验证请求传递 execution ID、响应含 answer 与完整 usage 字段、响应不含 `messages`；无用量的 Agent 失败仍返回安全错误响应并允许 Spring 标为 `UNAVAILABLE`。
- [x] **Step 1: Write route tests first.** Verify execution ID propagation, answer and all usage fields in the response, absence of `messages`, and a safe error response on an Agent failure without usage so Spring can mark it `UNAVAILABLE`.

- [x] **步骤 2：运行定向测试并确认失败。** 运行 `cd agent-service; pytest tests/test_app.py -q`。
- [x] **Step 2: Run the targeted tests and confirm failure.** Run `cd agent-service; pytest tests/test_app.py -q`.

- [x] **步骤 3：更新请求模型、上下文和响应映射。** 使用 Pydantic UUID 验证；仅序列化 `answer` 和摘要字段，不在异常日志中输出请求体或模型内容。
- [x] **Step 3: Update request model, context, and response mapping.** Validate UUID with Pydantic; serialize only `answer` and summary fields, and do not log request bodies or model content in exceptions.

- [x] **步骤 4：运行 Python Agent 测试集。** 运行 `cd agent-service; pytest -q`；预期现有工具 schema 测试和新增测试通过。
- [x] **Step 4: Run the Python Agent test suite.** Run `cd agent-service; pytest -q`; expect existing tool-schema and new tests to pass.

### Task 3: Spring DTOs and Metering Database / 任务 3：Spring DTO 与计量数据库

**文件 / Files：**

- 修改：`backend/src/main/java/com/excelai/agent/AgentService.java`
- Modify: `backend/src/main/java/com/excelai/agent/AgentService.java`
- 新建：`backend/src/main/java/com/excelai/agent/AgentResult.java`、`AgentUsageSummary.java`
- Create: `backend/src/main/java/com/excelai/agent/AgentResult.java` and `AgentUsageSummary.java`
- 新建：`backend/src/main/java/com/excelai/task/LlmUsageRecord.java`、`LlmUsageRepository.java`
- Create: `backend/src/main/java/com/excelai/task/LlmUsageRecord.java` and `LlmUsageRepository.java`
- 新建：`backend/src/main/resources/db/migration/V3__llm_usage_metering.sql`
- Create: `backend/src/main/resources/db/migration/V3__llm_usage_metering.sql`
- 测试：`backend/src/test/java/com/excelai/agent/AgentServiceTest.java`、`backend/src/test/java/com/excelai/task/LlmUsageRepositoryTest.java`
- Test: `backend/src/test/java/com/excelai/agent/AgentServiceTest.java` and `backend/src/test/java/com/excelai/task/LlmUsageRepositoryTest.java`

**接口 / Interfaces：**

- `AgentService.execute(...) -> AgentResult`；`AgentResult(String answer, AgentUsageSummary usage)`；`AgentUsageSummary` 使用与 Python 同名字段及 Java `Long` 可空计数。
- `AgentService.execute(...) -> AgentResult`; `AgentResult(String answer, AgentUsageSummary usage)`; `AgentUsageSummary` uses the Python field names and nullable Java `Long` counters.
- `LlmUsageRepository.save(LlmUsageRecord record)` 保存一条执行测量；`execution_id` 唯一、`task_id` 外键指向现有任务表。
- `LlmUsageRepository.save(LlmUsageRecord record)` persists one execution measurement; `execution_id` is unique and `task_id` references the existing task table.
- `TaskWorker` 为每次 Agent 调用生成 `UUID executionId`，并将 `taskId`、`executionId` 同时传给 `AgentService` 和 Agent。
- `TaskWorker` creates a `UUID executionId` for each Agent call and passes both `taskId` and `executionId` to `AgentService` and the Agent.

- [x] **步骤 1：先写 AgentService 测试。** 验证 UUID 请求字段、成功响应反序列化 nullable token 字段、Agent 网络失败映射为 `AgentResult` 缺失用量或明确异常，不记录响应正文。
- [x] **Step 1: Write AgentService tests first.** Verify UUID request fields, successful response parsing with nullable token fields, Agent network failure becoming missing usage or a clear exception, and no response-body logging.

- [x] **步骤 2：先写 migration/repository 测试。** 验证唯一 execution ID、任务外键、状态约束、nullable token 字段；复用现有 PostgreSQL 测试模式，若无数据库测试设施则通过实体映射测试并在任务 5 做 Compose migration 验证。
- [x] **Step 2: Write migration/repository tests first.** Verify unique execution ID, task foreign key, status constraint, and nullable token fields; reuse existing PostgreSQL test conventions, or use mapping tests and verify the migration with Compose in Task 5 if no database test setup exists.

- [x] **步骤 3：运行 Java 定向测试确认失败。** 使用项目 Maven 命令运行新增测试。
- [x] **Step 3: Run targeted Java tests and confirm failure.** Run the new tests with the project's Maven command.

- [x] **步骤 4：定义 Java DTO 并让 AgentService 返回 `AgentResult`。** 从 Agent 响应只解析回答和 usage；请求 body 发送任务 ID、execution ID 及现有业务字段。
- [x] **Step 4: Define Java DTOs and make AgentService return `AgentResult`.** Parse only answer and usage from the Agent response; send task ID, execution ID, and existing business fields in the request.

- [x] **步骤 5：新增 Flyway migration 与记录实体/repository。** 表名为 `llm_usage_record`；对 `execution_id` 建唯一约束；计数列允许 NULL；状态限于三种批准值；记录时间使用数据库默认 UTC 时间戳。
- [x] **Step 5: Add the Flyway migration, record entity, and repository.** Name the table `llm_usage_record`; add a unique constraint on `execution_id`; allow NULL counters; constrain status to the three approved values; default record time to a database UTC timestamp.

- [x] **步骤 6：运行 Java 定向测试。** 运行 `mvn -q -f backend/pom.xml test`；预期通过。
- [x] **Step 6: Run targeted Java tests.** Run `mvn -q -f backend/pom.xml test`; expect success.

### Task 4: Task Worker Metering Failure Semantics / 任务 4：任务 Worker 的计量故障语义

**文件 / Files：**

- 修改：`backend/src/main/java/com/excelai/task/TaskWorker.java`
- Modify: `backend/src/main/java/com/excelai/task/TaskWorker.java`
- 测试：`backend/src/test/java/com/excelai/task/TaskWorkerUsageTest.java`
- Test: `backend/src/test/java/com/excelai/task/TaskWorkerUsageTest.java`

**接口 / Interfaces：**

- Worker 接收 `AgentResult` 和 `LlmUsageRepository`；执行返回后立即尝试保存 usage，再执行原有文件校验和任务状态逻辑。
- Worker consumes `AgentResult` and `LlmUsageRepository`; immediately attempt to persist usage after Agent returns, then run the existing file validation and task-state logic.
- Agent 无摘要时，以相同 execution ID 保存 `UNAVAILABLE`，计数保持 NULL；保存异常仅输出任务 ID、执行 ID和异常类型等元数据。
- If Agent has no summary, save `UNAVAILABLE` with the same execution ID and NULL counters; persistence exceptions log metadata such as task ID, execution ID, and exception type only.

- [x] **步骤 1：写 Worker 行为测试。** 覆盖正常结果保存 COMPLETE/PARTIAL 摘要、结果文件校验失败仍保存已返回的摘要、Agent 超时记录 UNAVAILABLE、repository 抛错不改变任务最终状态且产生无敏感内容的告警日志。
- [x] **Step 1: Write worker behavior tests.** Cover saving COMPLETE/PARTIAL summaries for normal results, persisting returned usage despite result-file validation failure, recording UNAVAILABLE on Agent timeout, and repository failure preserving final task state while emitting a log without sensitive content.

- [x] **步骤 2：运行测试确认失败。** 运行 `mvn -f backend/pom.xml -Dtest=TaskWorkerUsageTest test`。
- [x] **Step 2: Run tests and confirm failure.** Run `mvn -f backend/pom.xml -Dtest=TaskWorkerUsageTest test`.

- [x] **步骤 3：在 Worker 的 Agent 调用边界持有 execution ID 并尝试保存记录。** 保存发生在后续结果文件校验之前；记录失败捕获为辅助错误，不吞掉或重写原任务错误。
- [x] **Step 3: Retain the execution ID at the Worker/Agent boundary and attempt to save the record.** Persist before later result-file validation; treat persistence failure as auxiliary without swallowing or rewriting the original task error.

- [x] **步骤 4：运行 Worker 与相关任务测试。** 运行 `mvn -q -f backend/pom.xml test`；预期通过。
- [x] **Step 4: Run worker and related task tests.** Run `mvn -q -f backend/pom.xml test`; expect success.

### Task 5: Integration Verification and Bilingual Cost Report / 任务 5：集成验证与双语成本对比报告

**文件 / Files：**

- 新建：`docs/superpowers/reports/2026-10-22-llm-usage-cost-comparison.md`
- Create: `docs/superpowers/reports/2026-10-22-llm-usage-cost-comparison.md`
- 按需修改：`agent-service/tests/` 下的合成工作簿 fixture 与运行脚本；仅新增执行本次手动对比所需文件。
- Modify as needed: synthetic workbook fixtures and runner under `agent-service/tests/`; add only files needed to run this manual comparison.

**接口 / Interfaces：**

- 实验记录以一次执行一行保存原始测量；报告每条工作流路线展示 3 次范围与中位数及逐次正确性。
- Store raw measurements one row per execution; the report shows each workflow/model's three-run range, median, and per-run correctness.
- 报告缺失运行和缺失字段，不为缺失数据计算费用或编造对照结果。
- Report missing runs and fields; do not calculate cost for missing data or fabricate comparison results.

- [x] **步骤 1：制作四类工作流的虚构输入及预期结果。** 基准脚本覆盖自由处理、合并清洗、两表对账、汇总；输入和指令固定，未使用用户/客户数据。
- [x] **Step 1: Prepare synthetic inputs and expected results for four workflows.** The benchmark runner covers freeform processing, merge-and-clean, two-table reconciliation, and summary with fixed synthetic inputs and instructions; no user/customer data was used.

- [x] **步骤 2：运行后端迁移与自动测试。** 隔离 Compose PostgreSQL 已应用 V3 表结构；Python 测试 11 项通过，命令为 `.agent-service.venvScriptspython.exe -m pytest -p no:cacheprovider -q`；Java 完整套件通过，命令为 `mvn -q -f backend/pom.xml test`。
- [x] **Step 2: Run database migration and automated tests.** V3 schema is present in the isolated Compose PostgreSQL database. All 11 Python tests passed with `.agent-service.venvScriptspython.exe -m pytest -p no:cacheprovider -q`; the full Java suite passed with `mvn -q -f backend/pom.xml test`.

- [x] **步骤 3：确认模型路线可用且凭据私密。** Ollama 提供 `qwen3.5:9b` 且本地健康检查通过。隔离环境远程配置指向 OpenAI `gpt-4o-mini`，不符合获准的 DeepSeek Flash 路线；未读取或打印密钥，也未发送远程请求。
- [x] **Step 3: Confirm model routes are ready and credentials stay private.** Ollama exposes `qwen3.5:9b` and passed the local health check. The isolated remote configuration points to OpenAI `gpt-4o-mini`, which does not match the approved DeepSeek Flash route; the key was neither read nor printed, and no remote request was sent.

- [ ] **步骤 4：运行 24 个合成基准任务。** 四种工作流 × 每种模型 3 次；保存每次执行 ID、结果正确性、完整用量状态、计数、耗时和远程费用输入。运行失败要保留缺失状态和原因类别，不伪造替代行。
- [ ] **Step 4: Run the 24 synthetic benchmark tasks.** Four workflows × three runs per model; retain each execution ID, correctness, measurement status, counts, duration, and remote-cost inputs. Preserve failed runs as missing with a reason category; do not create substitute measurements.

- [x] **步骤 5：核对 DeepSeek 官方价格并计算费用。** 已于 2026-10-22 核对官方美元峰谷费率并写入报告；由于远程样本未运行，没有计算或估造远程费用。
- [x] **Step 5: Verify official DeepSeek rates and calculate fees.** Official USD peak/off-peak rates were checked on 2026-10-22 and recorded in the report; no remote fee was calculated or fabricated because remote samples did not run.

- [x] **步骤 6：写双语报告并做安全复核。** 报告列出本地每种工作流 3 次的成功/失败、可用用量和结果核对状态；远程未运行。本地 API 费标为零，电费/硬件成本标为未计量；报告不含秘密、提示词或工作簿明细。
- [x] **Step 6: Write the bilingual report and review it for safety.** The report lists three local attempts per workflow, success/failure, available usage, and result-check status; remote runs are marked not run. Local API fees are zero and electricity/hardware costs are unmeasured. The report contains no secrets, prompts, or workbook details.

- [ ] **步骤 7：检查最终差异和双语一致性。** 仍需在最终报告完成远程对照、合并/对账错误诊断后做最终复核；本次报告和规格改动已提供中英文，未改套餐额度，`.superpowers/` 试跑脚本仍为忽略文件。
- [ ] **Step 7: Review the final diff and bilingual consistency.** Final review remains pending until the remote comparison and merge/reconciliation failure diagnosis are complete. This report and spec edits are bilingual, plan quotas were not changed, and the `.superpowers/` runner remains ignored.

## 当前阻塞 / Current Blockers

- 远程私密配置不是获准的 DeepSeek Flash 路线。用户需在本机私密 `.env` 配置 DeepSeek 路由后，才能执行 12 次远程调用；不要在聊天中发送 API 密钥。
- The private remote configuration is not the approved DeepSeek Flash route. The user must configure DeepSeek in the private local `.env` before the 12 remote calls can run; do not send the API key in chat.
- 本地合并清理与对账的 6 个计划样本都以 Agent HTTP 状态异常失败，并按设计记录为 `UNAVAILABLE`。需要先定位异常来源，再补测。
- All six planned local merge/clean and reconciliation samples failed with an Agent HTTP status exception and were recorded as `UNAVAILABLE` as designed. The exception source must be identified before rerunning them.

## 自审记录 / Self-Review Record

- 规格覆盖：Agent 每轮用量汇总由任务 1/2 覆盖；Java 传输与数据库由任务 3 覆盖；Worker 故障语义由任务 4 覆盖；24 次实验、费率引用与报告由任务 5 覆盖。
- Spec coverage: Task 1/2 cover per-turn Agent aggregation; Task 3 covers Java transport and database; Task 4 covers Worker failure semantics; Task 5 covers the 24-run experiment, rate citations, and report.
- 接口一致性：Python `UsageSummary` 与 HTTP `usage` 字段一一对应；Java `AgentUsageSummary` 使用同名字段；`execution_id` 从 Spring 生成并贯穿请求、记录和报告。
- Interface consistency: Python `UsageSummary` fields map one-to-one to HTTP `usage`; Java `AgentUsageSummary` uses the same names; Spring-generated `execution_id` flows through request, record, and report.
- 价格不进入业务代码；官方费率仅在运行时核查后写入带检索日期的报告。
- Rates stay out of business code; verify official rates at experiment time and record them only in the report with their lookup date.
