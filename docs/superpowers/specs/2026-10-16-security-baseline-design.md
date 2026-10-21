# 安全基线设计 / Security Baseline Design

## 目标 / Goal

移除已提交的默认凭据；要求访问 Python Agent 时完成鉴权；默认仅允许本机访问 PostgreSQL、Redis 和 Agent 的宿主机端口。

Remove committed default credentials, require authentication for access to the Python Agent, and make PostgreSQL, Redis, and Agent host ports locally reachable only by default.

## 背景 / Context

当前 Compose 文件为 PostgreSQL、Agent 共享令牌和 JWT 密钥提供弱默认值，Spring 配置也重复设置了默认值。Python `/agent/chat` 路由不需要鉴权，但 Agent 进程持有调用 Java 工具注册表的权限。PostgreSQL、Redis 和 Agent 的端口发布时没有指定宿主机绑定地址。

The current Compose file provides weak fallback values for PostgreSQL, the Agent shared token, and the JWT key; Spring configuration also repeats defaults. The Python `/agent/chat` route accepts unauthenticated requests even though the Agent process can call the privileged Java tool registry. PostgreSQL, Redis, and Agent ports are published without a host bind address.

## 设计 / Design

1. **密钥必填 / Require secrets.** Compose 在 `POSTGRES_PASSWORD`、`AGENT_INTERNAL_TOKEN` 或 `JWT_SECRET` 缺失或为空时必须在配置阶段失败。移除 `application.yml` 中的密钥默认值，使独立运行的 Spring 进程在缺少必需密钥时也会失败。Spring 启动时拒绝仓库中已有的旧默认值和明显的 `replace-with-...` 占位值；Python Agent 启动时拒绝缺失、空白或占位的共享令牌。保留 JWT 密钥至少 32 字节的现有要求。

   Compose must fail during configuration if `POSTGRES_PASSWORD`, `AGENT_INTERNAL_TOKEN`, or `JWT_SECRET` is missing or empty. Remove secret defaults from `application.yml` so a standalone Spring process also fails when required secrets are absent. Spring startup must reject the repository's known legacy defaults and obvious `replace-with-...` placeholders; Python Agent startup must reject a missing, blank, or placeholder shared token. Preserve the existing 32-byte minimum for JWT signing keys.

2. **Agent 入站鉴权 / Authenticate inbound Agent requests.** Python Agent 的 `/agent/chat` 和 `/tools` 路由必须要求 `X-Agent-Token`，并用恒定时间比较校验令牌。`/health` 保持公开，以支持容器健康检查。Spring 的 `AgentService` 调用 `/agent/chat` 时必须携带共享令牌；现有 Python Java 工具客户端继续使用同一个令牌访问 Java 注册表。

   Python Agent must require `X-Agent-Token` on `/agent/chat` and `/tools`, validating it with a constant-time comparison. Keep `/health` public for container health checks. Spring's `AgentService` must send the shared token to `/agent/chat`; the existing Python Java tool client continues to use the same token for the Java registry.

3. **端口绑定 / Bind host ports.** PostgreSQL、Redis 和 Agent 的宿主机端口默认绑定到 `127.0.0.1`，同时保留可配置的宿主机端口号。Backend 端口映射保持不变，因为它承载面向用户的应用。

   Bind PostgreSQL, Redis, and Agent host ports to `127.0.0.1` by default while retaining configurable host port numbers. Leave the Backend port mapping unchanged because it serves the user-facing application.

4. **示例配置和说明 / Example configuration and setup docs.** 修改 `.env.example`，使其不包含可直接使用的凭据。说明在运行 `docker compose up` 前如何生成三个互相独立的密钥；不作修改直接复制示例文件时，启动必须失败。

   Change `.env.example` so it contains no usable credentials. Document how to generate three independent values before running `docker compose up`; copying the example unchanged must fail closed.

## 不在本次范围内 / Out of Scope

限流、认证 DTO 校验、全局异常处理、健康检查正确性、上传校验、POI/EasyExcel 升级、CI、数据库迁移、任务恢复和可观测性属于后续事项。

Rate limiting, authentication DTO validation, global exception handling, health-check correctness, upload validation, POI/EasyExcel upgrades, CI, database migrations, task recovery, and observability are later work items.

## 验收标准 / Acceptance Criteria

- 未设置或设置为空的三个必需密钥，都会导致 `docker compose config` 失败。
- Spring 在 JWT 密钥或 Agent 令牌缺失、或为已知占位值时启动失败；仍拒绝短于 32 字节的 JWT 密钥。
- Agent 在共享令牌缺失、为空或为已知占位值时启动失败。
- 对 `/agent/chat` 和 `/tools` 的无令牌或错误令牌请求返回 401；有效的内部调用继续工作；`/health` 无令牌时仍可访问。
- Backend 调用 `/agent/chat` 时携带共享令牌。
- PostgreSQL、Redis 和 Agent 的宿主机端口映射绑定到 loopback；Backend 端口映射保持不变。
- `.env.example` 不含可用密钥，启动文档说明如何提供密钥。

- `docker compose config` fails when any of the three required secrets is unset or empty.
- Spring fails at startup when the JWT secret or Agent token is absent or is a known placeholder; JWT keys shorter than 32 bytes remain rejected.
- Agent startup fails when its shared token is absent, empty, or a known placeholder.
- Requests to `/agent/chat` and `/tools` with a missing or invalid token receive 401; valid internal calls continue to work; `/health` stays accessible without a token.
- Backend sends the shared token to `/agent/chat`.
- PostgreSQL, Redis, and Agent port mappings bind to loopback; Backend's mapping remains unchanged.
- `.env.example` has no usable secret values and setup documentation explains how to provide them.

## 约束 / Constraints

- 复用现有的 `AGENT_INTERNAL_TOKEN`，不新增第二个凭据或依赖。
- 保留 Java 工具注册表现有的授权校验和恒定时间令牌比较。
- 改动仅限于本设计描述的安全基线。

- Reuse the existing `AGENT_INTERNAL_TOKEN`; do not add a second credential or dependency.
- Preserve the existing Java Tool Registry authorization checks and constant-time token comparison.
- Keep changes limited to the security baseline described here.
