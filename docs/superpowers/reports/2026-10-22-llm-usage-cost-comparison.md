# LLM Usage and Cost Pilot / LLM 用量与成本试跑

## 目的与边界 / Purpose and Scope

本报告记录 2026-10-22 使用虚构 CSV 数据验证用量采集和本地模型表现的试跑。目标模型为本地 Ollama `qwen3.5:9b`，以及计划中的远程 DeepSeek `deepseek-flash`。本报告不包含用户工作簿、真实任务提示词、API 密钥或私密数据。

This report records the 2026-10-22 pilot using synthetic CSV data to verify usage capture and local-model behavior. The target routes were local Ollama `qwen3.5:9b` and planned remote DeepSeek `deepseek-flash`. No user workbooks, real task prompts, API keys, or private data are included.

计划样本是四类工作流各运行 3 次、每条模型路线共 12 次。隔离环境完成了 12 次本地计划任务；远程 12 次没有运行，因为当前私密配置解析为 OpenAI 兼容的 `gpt-4o-mini` 路线，而非已批准的 DeepSeek 路线。没有使用或展示密钥，也没有向该路线发送请求。

The planned sample was three runs for each of four workflows, or 12 runs per model route. The isolated environment completed the 12 planned local attempts. The 12 remote runs were not made because the current private configuration resolved to an OpenAI-compatible `gpt-4o-mini` route rather than the approved DeepSeek route. No key was used or displayed, and no request was sent to that route.

## 本地结果 / Local Results

| 工作流 / Workflow | 成功样本 / Successful runs | 每次输入/输出 token / Input/output tokens per run | 模型调用 / Model calls | 模型耗时 / Model duration | 结果核对 / Result check |
|---|---:|---:|---:|---:|---|
| 自由处理 / Freeform | 3/3 | 6,058 / 367 | 5 each | 9.478–9.701 s; median 9.656 s | 均生成结果文件；未独立逐单元格核验 / Result files were created; cell-level correctness was not independently checked |
| 汇总 / Summary | 3/3 | 4,768 / 343 | 4 each | 8.708–8.851 s; median 8.795 s | 3 行纳入、2 个分组、0 行异常；与合成预期相符 / 3 rows included, 2 groups, 0 exception rows; matches the synthetic expectation |
| 合并清理 / Merge and clean | 0/3 | 缺失 / Missing | 缺失 / Missing | 缺失 / Missing | 3 次失败，状态 `UNAVAILABLE` / Three failures, status `UNAVAILABLE` |
| 两表对账 / Two-table reconciliation | 0/3 | 缺失 / Missing | 缺失 / Missing | 缺失 / Missing | 3 次失败，状态 `UNAVAILABLE` / Three failures, status `UNAVAILABLE` |

自由处理与汇总成功样本均采集到输入和输出 token，但 Ollama 没有提供缓存输入 token，因此数据库状态为 `PARTIAL`。失败样本记录为 `UNAVAILABLE`，token 为空；这不代表实际成本为零。Agent 记录了 HTTP 状态异常类型，当前还没有定位到该异常的具体来源。

Successful freeform and summary runs recorded input and output tokens, but Ollama did not provide cached-input token counts, so the database status is `PARTIAL`. Failed runs are recorded as `UNAVAILABLE` with null token fields; this does not mean their actual cost was zero. The Agent recorded an HTTP status exception type, but its precise source has not yet been identified.

本地 API 调用费为 **$0**。电力、硬件折旧和设备空闲功耗没有测量，因此本地总成本尚不能精确计算。模型调用耗时仅代表模型请求时间，不包含上传、文件读取、Java 工作簿处理或用户等待的完整任务耗时。

Local API charges are **$0**. Electricity, hardware depreciation, and idle power were not measured, so total local cost cannot yet be calculated precisely. Model duration covers model calls only; it excludes upload, file reading, Java workbook processing, and the user's full task wait time.

## 远程计价口径 / Remote Pricing Basis

检索日期：2026-10-22。DeepSeek 官方价格页将 API 模型名列为 `deepseek-flash`，并按峰谷时段及缓存命中区分价格。单位为美元/百万 token：

Lookup date: 2026-10-22. DeepSeek's official pricing page lists the API model name as `deepseek-flash` and varies rates by peak/off-peak period and cache hits. Rates are USD per million tokens:

| 计量项 / Meter | 谷时 / Off-peak | 峰时 / Peak |
|---|---:|---:|
| 缓存命中输入 / Cache-hit input | $0.003 | $0.006 |
| 未缓存输入 / Cache-miss input | $0.15 | $0.30 |
| 输出 / Output | $0.60 | $1.20 |

费用公式为：`(缓存命中输入 token × 命中费率 + 未缓存输入 token × 未命中费率 + 输出 token × 输出费率) ÷ 1,000,000`。远程实验没有执行，因此本报告不估算 DeepSeek 费用，也不把其他供应商的费率替代为 DeepSeek 结果。费率可能调整，正式定价前应重新核对[DeepSeek 官方价格页](https://api-docs.deepseek.com/quick_start/pricing/)。

The cost formula is: `(cache-hit input tokens × cache-hit rate + cache-miss input tokens × cache-miss rate + output tokens × output rate) ÷ 1,000,000`. The remote experiment did not run, so this report does not estimate DeepSeek charges or substitute another provider's rates for DeepSeek results. Rates may change; check the [official DeepSeek pricing page](https://api-docs.deepseek.com/quick_start/pricing/) again before setting customer prices.

## 结论与后续 / Findings and Next Steps

已验证：`qwen3.5:9b` 的自由处理与汇总路径可以生成结果并保存任务级用量。当前数据还不足以确定日卡/月卡的安全次数或单次定价：远程路线尚未对照，合并和对账路线失败，且本地电力成本未测。不要据此承诺不限量使用或制定最终价格。

Verified: the `qwen3.5:9b` freeform and summary routes produced results and stored task-level usage. The data is not sufficient to set safe day/month-card limits or per-task pricing: the remote route has not been compared, merge and reconciliation failed, and local electricity cost is unmeasured. Do not use this pilot to promise unlimited use or set final prices.

继续远程试跑前，需要在本机私密 `.env` 配置 DeepSeek 的供应商、`https://api.deepseek.com` 地址、模型 `deepseek-flash` 和对应 API 密钥；不要把密钥粘贴到聊天或文档。还需要定位 typed workflow 的 Agent HTTP 状态异常，再重跑合并清理和对账样本。

Before the remote pilot can continue, configure DeepSeek's provider, `https://api.deepseek.com` base URL, `deepseek-flash` model, and matching API key in the private local `.env`; do not paste the key into chat or documentation. The Agent HTTP status failure for typed workflows also needs diagnosis before rerunning merge/clean and reconciliation samples.
