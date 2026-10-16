# 安全基线实施计划 / Security Baseline Implementation Plan

> **执行方式 / Execution:** 在当前会话中由 Codex 按任务顺序直接实现。 / Codex implements the tasks inline in this session.

**目标 / Goal:** 移除可用的提交内默认密钥，保护 Python Agent 的内部路由，并将数据库、缓存和 Agent 的宿主机端口默认限制到本机。 / Remove usable committed default secrets, protect the Python Agent's internal routes, and bind database, cache, and Agent host ports to loopback by default.

**架构 / Architecture:** Compose 对必需密钥 fail-closed，Spring 启动时拒绝已知占位密钥，Python 与 Spring 使用现有 `AGENT_INTERNAL_TOKEN` 双向保护 Agent HTTP 调用。Compose 仅将 PostgreSQL、Redis 和 Agent 的宿主机端口绑定到 `127.0.0.1`。 / Compose fails closed for required secrets, Spring rejects known placeholder secrets at startup, and Python and Spring use the existing `AGENT_INTERNAL_TOKEN` to protect Agent HTTP calls. Compose binds only PostgreSQL, Redis, and Agent host ports to `127.0.0.1`.

**技术栈 / Tech Stack:** Docker Compose、Spring Boot、Python FastAPI、现有 JWT/HTTP 客户端。 / Docker Compose, Spring Boot, Python FastAPI, and existing JWT/HTTP clients.

**规格 / Spec:** [安全基线设计 / Security Baseline Design](../specs/2026-10-16-security-baseline-design.md)

## 全局约束 / Global Constraints

- 文档与新增/修改的注释均使用中英双语。 / Documentation and all added or modified comments must be bilingual.
- 复用现有 `AGENT_INTERNAL_TOKEN`，不新增依赖或第二个令牌。 / Reuse `AGENT_INTERNAL_TOKEN`; add no dependency or second token.
- 保留 Java Tool Registry 的现有授权校验与恒定时间比较。 / Preserve existing Java Tool Registry authorization and constant-time comparison.
- Backend 的宿主机端口映射保持不变。 / Leave the Backend host-port mapping unchanged.

## 评审重点 / Review Focus

- 空值或已知占位值不得允许应用以弱凭据启动。 / Empty or known placeholder values must not allow startup with weak credentials.
- 缺失或错误的 Agent 令牌必须返回 401，健康检查保持公开。 / Missing or invalid Agent tokens must return 401; health checks remain public.
- Compose 网络内服务仍须通过服务名互通，本机仍可通过 loopback 调试。 / Services must retain Compose-network communication by service name and local debugging through loopback.
- 复制 `.env.example` 后不得得到可运行的默认密钥。 / Copying `.env.example` must not produce usable default secrets.

---

### 任务 1：密钥配置与端口绑定 / Task 1: Secret Configuration and Port Binding

**文件 / Files:**
- 修改 / Modify: `docker-compose.yml`
- 修改 / Modify: `backend/src/main/resources/application.yml`
- 新建 / Create: `backend/src/main/java/com/excelai/config/SecuritySecretsValidator.java`
- 修改 / Modify: `.env.example`, `README.md`

- [x] 为 Compose 中 `POSTGRES_PASSWORD`、`AGENT_INTERNAL_TOKEN`、`JWT_SECRET` 使用必填插值；移除 Spring 对这三个配置的默认值，并在 Spring 启动时拒绝已知旧默认值和 `replace-with-` 占位值。 / Make Compose require `POSTGRES_PASSWORD`, `AGENT_INTERNAL_TOKEN`, and `JWT_SECRET`; remove Spring defaults and reject known legacy values and `replace-with-` placeholders at startup.
- [x] 保留 JWT 最低 32 字节校验；`.env.example` 的三个密钥置空，并在 README 增加中英双语的随机密钥生成说明。 / Preserve the 32-byte JWT minimum; leave the three `.env.example` secrets empty and add bilingual README instructions for generating random secrets.
- [x] 将 PostgreSQL、Redis、Agent 的端口发布绑定到 `127.0.0.1`；保留 Backend 映射。 / Bind PostgreSQL, Redis, and Agent published ports to `127.0.0.1`; preserve the Backend mapping.
- [x] 检查配置差异及双语文档/注释；`git diff --check` 已通过。 / Review configuration changes and bilingual docs/comments; `git diff --check` passed.

### 任务 2：保护 Agent 入站路由 / Task 2: Protect Inbound Agent Routes

**文件 / Files:**
- 修改 / Modify: `agent-service/app.py`
- 修改 / Modify: `agent-service/.env.example`
- 修改 / Modify: `backend/src/main/java/com/excelai/agent/AgentService.java`

- [x] 在加载 `agent_graph` 前加载本地 `.env`；Agent 启动时拒绝缺失、空白或占位共享令牌。 / Load local `.env` before importing `agent_graph`; reject a missing, blank, or placeholder shared token at Agent startup.
- [x] 使用恒定时间比较验证 `/agent/chat` 和 `/tools` 的 `X-Agent-Token`；保持 `/health` 无鉴权。 / Validate `X-Agent-Token` on `/agent/chat` and `/tools` with a constant-time comparison; keep `/health` unauthenticated.
- [x] Spring 调用 `/agent/chat` 时发送现有 `AGENT_INTERNAL_TOKEN`。 / Send the existing `AGENT_INTERNAL_TOKEN` when Spring calls `/agent/chat`.
- [x] 更新直连 Agent 的测试指南以携带令牌；差异检查通过，未运行测试。 / Update the direct Agent test guide to include the token; diff check passed and tests were not run.
