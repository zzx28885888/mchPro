# LLM 用量采集与成本对比设计

# LLM Usage Metering and Cost Comparison Design

## 目标 / Goal

为每次表格处理任务记录模型供应商、模型名称、输入/输出/缓存输入 token 数、模型调用次数和耗时；据此比较当前本地 Ollama 与一个远程 API 的用量和成本。用量记录只保存计量元数据，不保存提示词、模型回答、工作簿内容或 API 密钥。完成试运营测算前，不确定单次、日卡或月卡的价格和任务上限。

Record the model provider, model name, input/output/cached-input token counts, model-call count, and model-call duration for each spreadsheet task. Use these records to compare the current local Ollama model with one remote API. Usage records contain metering metadata only; they do not store prompts, model answers, workbook contents, or API keys. Do not set one-time, day-pass, or month-pass prices or task limits before the pilot cost analysis.

## 当前项目状态 / Current Project State

- 项目默认启用 `LLM_PROVIDER=ollama` 和 `OLLAMA_MODEL=qwen3:8b`；远程 OpenAI 兼容 API 只有在切换到 remote 模式并配置本地私密环境变量后才会启用。
- The project currently enables `LLM_PROVIDER=ollama` and `OLLAMA_MODEL=qwen3:8b` by default. A remote OpenAI-compatible API is enabled only after switching to remote mode and configuring private local environment variables.
- Python Agent 使用 LangGraph 与 LangChain 的 `ChatOllama` / `ChatOpenAI`。Agent HTTP 响应当前返回回答和消息；Spring 的 `AgentService` 只读取回答，项目没有持久化模型 token 用量。
- The Python Agent uses LangGraph and LangChain `ChatOllama` / `ChatOpenAI`. The Agent HTTP response currently returns the answer and messages; Spring's `AgentService` reads only the answer, and the project does not persist model token usage.
- 现有 `usage_record` 用于用户套餐任务额度，不代表 LLM token 费用；本设计新增独立的模型用量记录，不改变用户套餐额度。
- Existing `usage_record` rows account for user plan task quotas, not LLM token cost. This design adds separate model-usage records and does not change user plan quotas.

## 批准的范围 / Approved Scope

- 记录每次 Agent 执行中所有模型轮次的用量汇总；通过任务 ID 和独立执行 ID 关联，以区分任务重试。
- Aggregate usage across all model turns in each Agent execution, linked by task ID and a unique execution ID so task retries remain distinguishable.
- 每次执行保存供应商、模型名、输入 token、输出 token、缓存输入 token、模型调用次数、模型调用总耗时和测量状态。
- Store provider, model name, input tokens, output tokens, cached input tokens, model-call count, total model-call duration, and measurement status for each execution.
- 对照模型为当前配置的本地 Ollama `qwen3:8b` 与 DeepSeek Flash 远程 API。远程 API 密钥只能由用户配置在本机私密 `.env` 中，不进入仓库、规格、计划、数据库或普通日志。
- Compare the currently configured local Ollama `qwen3:8b` model with the DeepSeek Flash remote API. The remote API key must be configured by the user in a private local `.env` file and must not enter the repository, specifications, plans, database, or ordinary logs.
- 使用虚构工作簿与任务描述，覆盖自由处理、合并清洗、两表对账、汇总四类工作流。每类工作流在每种模型下运行 3 次，共 12 次本地执行和 12 次远程执行。
- Use synthetic workbooks and task instructions for freeform processing, merge-and-clean, two-table reconciliation, and summary workflows. Run each workflow three times per model, for 12 local runs and 12 remote runs.
- 报告每种工作流的 token 用量、远程 API 费用、模型调用时间和结果正确性；不把小样本结果描述为保证值。
- Report token usage, remote API cost, model-call time, and result correctness for each workflow. Do not present this small pilot sample as a guarantee.
- 不新增面向用户的套餐、付款或管理页面；不改变现有任务扣额、处理流程和结果 API。
- Do not add user-facing plan, payment, or admin pages. Do not change existing task quota charging, task processing, or result APIs.

## 方案 / Design

### 1. 用量采集与数据流 / Usage Collection and Data Flow

Spring 为每次 Agent 执行生成 UUID `execution_id`，并与现有任务 ID 一同传给 Python Agent。Python 在每次模型响应后，从提供方返回的用量元数据中提取 token 计数；同时使用单调计时器记录该轮调用耗时。Agent 将所有轮次汇总后，只返回回答和用量摘要，不再把原始 LangGraph 消息/工具结果传回 Spring。Spring 的 `AgentService` 只使用回答；任务 Worker 保存用量摘要，并继续执行现有结果文件校验和任务状态更新。

Spring generates a UUID `execution_id` for each Agent execution and sends it to the Python Agent with the existing task ID. After each model response, Python extracts token counts from provider usage metadata and measures that model-call duration with a monotonic timer. The Agent aggregates all turns and returns only the answer and usage summary; it no longer sends raw LangGraph messages or tool results back to Spring. Spring's `AgentService` uses only the answer; the task worker persists usage and continues existing result-file validation and task-status updates.

新增 `llm_usage_record` 表，每次 Agent 执行一行，至少包括：`execution_id`、`task_id`、`provider_code`、`model_name`、`input_tokens`、`output_tokens`、`cached_input_tokens`、`model_call_count`、`llm_duration_ms`、`measurement_status` 和 `recorded_at`。`execution_id` 唯一；token 数字段允许为空，以区别“供应商未提供”与真实的零用量。

Add one row per Agent execution to a new `llm_usage_record` table. It contains at least `execution_id`, `task_id`, `provider_code`, `model_name`, `input_tokens`, `output_tokens`, `cached_input_tokens`, `model_call_count`, `llm_duration_ms`, `measurement_status`, and `recorded_at`. `execution_id` is unique. Token-count columns are nullable so “not provided by provider” is distinct from a measured zero.

测量状态使用 `COMPLETE`、`PARTIAL`、`UNAVAILABLE`。所有模型轮次都有计数时为 `COMPLETE`；至少一轮有计数但另有轮次缺失时为 `PARTIAL`；没有可用计数时为 `UNAVAILABLE`。不以本地 tokenizer 估算值冒充供应商实际计数，也不以零替代缺失数据。

Use measurement states `COMPLETE`, `PARTIAL`, and `UNAVAILABLE`. Use `COMPLETE` when every model turn provides counts, `PARTIAL` when some turns provide counts and others do not, and `UNAVAILABLE` when no counts are available. Do not present local tokenizer estimates as provider-reported usage and do not replace missing data with zero.

如果 Agent 执行返回用量摘要后，后续结果文件校验或保存失败，仍应保存已知模型用量。若 Agent 调用超时或失败且没有用量摘要，保存 `UNAVAILABLE` 记录；用量表写入失败不得覆盖或回滚已完成的用户任务，但须通过不含提示词、回答或工作簿内容的日志标出测量失败。

If the Agent returns a usage summary but later result-file validation or persistence fails, save the known model usage anyway. If the Agent call times out or fails without a usage summary, save an `UNAVAILABLE` record. Failure to persist metering data must not overwrite or roll back a completed user task, but it must produce a log entry that contains no prompt, answer, or workbook content.

### 2. 成本计算 / Cost Calculation

远程 API 费用在分析报告中计算，不写死在业务代码或用户额度表中。对每次执行，费用为 `((输入 token - 缓存输入 token) × 未缓存输入单价 + 缓存输入 token × 缓存单价 + 输出 token × 输出单价) ÷ 1,000,000`；报告按供应商公开价格和调用时间选择峰谷费率，并注明价格来源、币种、费率时间段和检索日期。DeepSeek 价格可能随峰谷时段和供应商调整而变化；每份报告必须引用当时的官方费率页面。

Calculate remote API costs in the analysis report rather than hard-coding them into business code or user quota tables. For each execution, cost is `((input tokens - cached input tokens) × uncached input rate + cached input tokens × cached input rate + output tokens × output rate) ÷ 1,000,000`. Select peak/off-peak rates from the provider's published prices and call time; record the price source, currency, applicable rate period, and lookup date. DeepSeek rates may vary by peak/off-peak period and provider updates, so each report must cite the official rate page used at the time.

本地 Ollama 的供应商 token 费用记为零；报告仍需列出 token 数和模型调用耗时。本地硬件、电费、服务器租赁或折旧属于独立运营成本；在没有明确部署硬件、功率/租赁费用和电价前，不将这些成本伪装成精确的单任务费用。

Local Ollama provider token fees are zero; the report still lists token counts and model-call duration. Local hardware, electricity, server rental, and depreciation are separate operating costs. Do not present them as precise per-task costs until the deployment hardware, power or rental cost, and electricity rate are known.

远程候选模型使用 DeepSeek API 的 OpenAI 兼容端点和 `deepseek-flash` 模型标识。官方费率页面与工具调用文档作为试验时的核价来源：<https://api-docs.deepseek.com/quick_start/pricing/> 和 <https://api-docs.deepseek.com/guides/tool_calls/>。本地用量字段参考 Ollama Chat API：<https://docs.ollama.com/api/chat>。

The remote comparison candidate uses DeepSeek's OpenAI-compatible endpoint and the `deepseek-flash` model identifier. Use the official pricing and tool-call documentation as the rate references for the experiment: <https://api-docs.deepseek.com/quick_start/pricing/> and <https://api-docs.deepseek.com/guides/tool_calls/>. Refer to the Ollama Chat API for local usage fields: <https://docs.ollama.com/api/chat>.

### 3. 对照实验 / Comparison Experiment

为四种现有工作流各准备一组虚构数据和固定任务描述。每种模型都运行同一组输入，每种工作流重复 3 次。记录每次运行的结果正确性、模型调用次数、输入/输出/缓存 token、模型调用总耗时，以及远程 API 的估算费用。报告逐项呈现 3 次结果范围和中位数，不只给合并后的单一均值。

Prepare synthetic data and fixed task instructions for each of the four existing workflows. Run the same inputs against both models and repeat each workflow three times per model. Record result correctness, model-call count, input/output/cached tokens, total model-call duration, and estimated remote API fee for each run. Show the three-run range and median for each workflow rather than only a single combined average.

所有测试工作簿均为人工生成的虚构内容，不使用客户或个人数据。结果正确性需与预期输出核对；自由处理的结果由人工检查，确定性工作流按已知行数、值和文件生成结果核对。

All benchmark workbooks use synthetic content created for this experiment; do not use customer or personal data. Check correctness against expected output. Manually review freeform results and verify deterministic workflows against known rows, values, and generated result files.

## 隐私与安全 / Privacy and Security

- 用量记录仅含计量元数据、任务关联 ID 和时间，不含用户提示词、模型回答、单元格/行数据、文件名、文件内容、API 密钥或完整 API 请求/响应。
- Usage records contain metering metadata, task-link IDs, and timestamps only. They must not contain user prompts, model answers, cell/row data, filenames, file contents, API keys, or full API requests/responses.
- 不将提示词、回答或表格内容添加到日志、异常消息、规格或计划中。
- Do not add prompts, answers, or workbook content to logs, exception messages, specifications, or plans.
- API 密钥仅通过本地私密环境变量提供；远程基准实验只发送本规格中定义的虚构输入。
- API keys are supplied only through private local environment variables; the remote benchmark sends only the synthetic inputs defined for this experiment.

## 错误处理与边界 / Error Handling and Boundaries

- 供应商未返回部分或全部 token 数时，保存可用字段并标明 `PARTIAL` 或 `UNAVAILABLE`；缺失字段不得进入费用计算。
- If a provider omits some or all token counts, save the available fields and mark the record `PARTIAL` or `UNAVAILABLE`; missing fields must not be used in cost calculations.
- 用量存储是辅助统计。存储失败不得使已完成的表格任务变为失败；但该次运行必须在报告中视为测量缺失。
- Metering is auxiliary. A storage failure must not turn a completed spreadsheet task into a failed task, but the run must be treated as missing from the cost report.
- 不实现实时余额扣费、动态套餐额度、token 预算拦截、用户成本展示、自动更新价格表或管理仪表板。
- Do not implement real-time balance charging, dynamic plan quotas, token-budget blocking, user-facing cost display, automatic price-table updates, or an admin dashboard.
- 远程 API 未配置凭据或本地 Ollama 未运行时，不伪造试验数据；明确记录未完成的模型路线，并在私密 `.env`/运行环境准备就绪后重跑对应样本。
- If remote API credentials are not configured or local Ollama is unavailable, do not fabricate benchmark data. Mark that model route incomplete and rerun its samples after its private `.env` or runtime is ready.

## 验收标准 / Acceptance Criteria

1. 每次 Agent 执行返回和持久化一条可关联到任务及执行 UUID 的模型用量记录；任务重试产生独立记录。
2. Each Agent execution returns and persists one usage record linked to its task and execution UUID; a task retry creates a separate record.
3. 多轮模型调用的 token 数、缓存输入 token 和模型调用耗时正确汇总；供应商缺失数据时状态准确标记。
4. Token counts, cached-input tokens, and model-call durations are correctly aggregated across multiple model turns; missing provider data is accurately marked.
5. 用量表和普通日志中不出现提示词、模型回答、工作簿内容或密钥。
6. Prompts, model answers, workbook contents, and keys do not appear in the usage table or ordinary logs.
7. 用量存储失败不会改变用户任务的成功/失败状态；对照报告会把缺失测量标为不可用于费用比较。
8. Metering persistence failure does not change the user's task success/failure state; the comparison report marks missing measurements as ineligible for cost comparison.
9. 对照报告包含本地 Qwen3:8B 和远程 DeepSeek Flash 的结果，每条工作流路线 3 次运行；费用注明官方价格来源与检索日期，并把本地固定运营成本单独列出或标明待测。
10. The comparison report includes local Qwen3:8B and remote DeepSeek Flash, with three runs per workflow on each route; remote costs cite the official rate source and lookup date, while local fixed operating costs are listed separately or marked as unmeasured.

## 排除范围 / Out of Scope

不在本次规格内实现套餐下单、人工收款、免费试用防蹭、邮箱验证、语音输入、任务预览、管理后台、订单表、付款二维码、费率同步、Ollama GPU 能耗计费或生产环境部署。

This specification does not implement plan ordering, manual payment collection, free-trial abuse prevention, email verification, voice input, task previews, an admin panel, order tables, payment QR codes, rate synchronization, Ollama GPU energy billing, or production deployment.
