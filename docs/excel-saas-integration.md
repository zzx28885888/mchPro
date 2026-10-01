# Excel SaaS business integration

## Goal

Bring the business capabilities from `excel-ai-saas-v2` into this repository while keeping this repository's V1.6 runtime and boundaries:

- Spring Boot / Java owns SaaS APIs, identity, file metadata, task lifecycle, plan enforcement, and the authoritative Java Tool Registry.
- PostgreSQL is the system of record; Redis stores task/session state and coordination data.
- The Python LangGraph service owns LLM-driven planning and Multi-Agent orchestration.
- Excel operations are Java tools. The model cannot access files, SQL, or the filesystem directly.
- DeepSeek Harness remains optional developer automation and is not part of the product request path.

## Business capability mapping

| Excel SaaS V2 capability | V1.6 destination |
|---|---|
| Register/login and JWT | Spring Boot public auth API; authenticated user identity is propagated as trusted task context |
| User-owned Excel upload/download | Spring Boot file API and storage service; every read/write checks file ownership |
| Async task creation/status | Spring Boot task API and worker; task state in PostgreSQL, Redis for live progress |
| LLM plan generation | LangGraph agent service, using the Java registry as its only business tool source |
| Read/filter/sort/top/export Excel | Java `@AgentTool` implementations, subject to plan permission, validation, timeout, and audit |
| Plans, monthly task quotas, file-size and tool-call limits | PostgreSQL plan and usage records, enforced by Spring Boot before dispatch and Java before execution |
| MySQL schema and connector | Replaced by PostgreSQL using this repository's existing datasource and Compose service |
| Embedded Excel SaaS page | `backend/src/main/resources/static/index.html`, served from `/` and wired to authenticated Spring Boot APIs |
| Multi-file merge/clean, reconciliation, and summary | Typed Spring task preview/create contract plus deterministic Java workflow tools and downloadable result summaries |

## Target request flow

```text
Browser
  -> Spring Boot auth / files / tasks API
  -> PostgreSQL ownership, plan, quota checks
  -> Redis task progress
  -> LangGraph task orchestration
  -> Java Tool Registry (trusted task context)
  -> permission + schema validation + timeout + audit
  -> Java Excel tools / owned file storage
  -> persisted workflow result summary (rules, counts, warnings, exceptions)
  -> LangGraph result
  -> Spring Boot task worker records output file ID and final task state
```

Task context (user ID, task ID, input file ID) must be supplied by the trusted Spring Boot-to-LangGraph service call and forwarded by the LangGraph-to-Java service call. It must never be accepted as authority from model-generated tool arguments. Tool calls must be restricted to files owned by that user and to tools enabled by that user's plan.

## Migration sequence

1. Add PostgreSQL schema for accounts, plans, file metadata, tasks, usage, and plan tool permissions; seed the existing FREE/BASIC/PRO plans and Excel tool grants.
2. Move auth and account APIs onto the V1.6 backend, preserving the V1.6 PostgreSQL/Redis infrastructure and Java 21 runtime.
3. Add private file storage and ownership-scoped upload, list, and download endpoints.
4. Add asynchronous task lifecycle and plan/usage enforcement; dispatch orchestration to LangGraph with signed or otherwise service-authenticated task context.
5. Register legacy Excel capabilities and typed merge/clean, reconciliation, summary, and export tools in Java `@AgentTool`; keep IDs and options out of model-controlled arguments.
6. Add multi-file typed task preview and confirmation, dynamic worksheet/field configuration, and result-summary display to the SaaS UI.
7. Apply the idempotent `infra/migrations/001_excel_workflows.sql` migration to existing databases.
8. Verify auth, upload, typed preview/create, tool execution, result download, cross-user denial, quota/file-size enforcement, and audit records.

## Compatibility decisions

- Do not introduce MySQL into the consolidated application; use the V1.6 PostgreSQL schema and Java 21 container runtime.
- Do not move the SaaS LLM planner into Spring Boot. LangGraph remains the product orchestration layer.
- Keep Harness optional. It is a developer workflow tool, not a dependency for the Excel SaaS request path.
- Treat the V2 source project as a read-only migration source; all integration edits belong in this repository.

## Current state

- PostgreSQL bootstrap has SaaS account, plan, file, task, usage, and plan-tool-permission tables; inserts use PostgreSQL `RETURNING id`.
- The static SaaS page is served at `http://localhost:8080/`; auth, upload/download, async task create/status, and `/api/me` APIs are wired to Spring Boot.
- Spring Boot authenticates users with JWT, stores uploaded and exported workbooks in the `excel_files` Docker volume, and tracks task progress in Redis.
- Task context is supplied by the backend to LangGraph and forwarded to Java with a shared internal token. Java verifies file ownership and plan tool grants before execution.
- Excel read/filter/sort/top/export are registered Java `@AgentTool` methods, preserving the V1.6 LangGraph → Java Tool Registry boundary.
- Merge/clean, reconcile, and summary are available as bounded typed workflows. The user reviews a deterministic preview before task creation; each task persists the input IDs, workflow options, operation summary, and row-level exceptions.
- The Excel rowset is held in Redis for the lifetime of task processing; output metadata and completed task state are stored in PostgreSQL.
- Runtime check confirmed homepage HTTP 200, PostgreSQL/Redis health, auth route response, five registered Excel tools, and Agent health. A paid/remote LLM task was not triggered during verification.
