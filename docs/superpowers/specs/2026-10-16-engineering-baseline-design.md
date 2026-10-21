# 工程基线设计 / Engineering Baseline Design

## 目标 / Goal

让数据库初始化和升级可追踪、锁定 Python Agent 的运行依赖，并使本地启动文档与容器运行行为一致且更安全。

Make database initialization and upgrades traceable, lock the Python Agent's runtime dependencies, and align local setup documentation with safer container behavior.

## 背景 / Context

PostgreSQL schema 目前由容器首次启动脚本初始化，现有版本迁移需要人工执行；Python 项目没有 `pyproject.toml` 或锁文件，README 中的 `python app.py` 也不会启动 FastAPI。Agent 与 Backend 容器均以 root 运行，Compose 仅为 PostgreSQL 和 Redis 配置健康检查，服务日志没有轮转限制。

The PostgreSQL schema is initialized by a first-start container script, and later migrations require manual execution. The Python project has no `pyproject.toml` or lockfile, and the README's `python app.py` command does not start FastAPI. Agent and Backend containers run as root; Compose health-checks only PostgreSQL and Redis, and container logs have no rotation limits.

## 设计 / Design

1. **Flyway 迁移 / Flyway migrations.** 将 `infra/init.sql` 转为 `V1__initial_schema.sql`，将现有工作流迁移转为 `V2__excel_workflows.sql`，放入 Spring 资源目录。使用 Spring Boot 管理的 `spring-boot-starter-flyway` 和 PostgreSQL 数据库模块。关闭 PostgreSQL entrypoint 的手动 SQL 挂载；Flyway 在 Backend 启动时迁移。配置 `baseline-on-migrate=true`、`baseline-version=1`：空数据库执行 V1 与 V2；已有非空 schema 的数据库在版本 1 建基线，再执行幂等的 V2。

   Convert `infra/init.sql` to `V1__initial_schema.sql` and the existing workflow migration to `V2__excel_workflows.sql` under Spring resources. Use Spring Boot's managed `spring-boot-starter-flyway` and Flyway's PostgreSQL database module. Remove the PostgreSQL entrypoint SQL mount; Flyway migrates at Backend startup. Configure `baseline-on-migrate=true` and `baseline-version=1`: an empty database runs V1 and V2, while an existing non-empty schema is baselined at version 1 and receives the idempotent V2 migration.

2. **Python 锁文件 / Python lockfile.** 新建 `agent-service/pyproject.toml`，声明 Python 3.13、现有运行依赖、缺失的 `python-dotenv`，以及仅供开发使用的 `pytest` 依赖组；生成并提交 `uv.lock`。Docker 构建用锁文件安装运行依赖，本地开发使用 `uv sync`。不再把 `requirements.txt` 作为第二个可变依赖清单。

   Add `agent-service/pyproject.toml` declaring Python 3.13, the current runtime dependencies, the missing `python-dotenv`, and a development-only `pytest` dependency group; generate and commit `uv.lock`. Docker installs runtime dependencies from the lockfile, and local development uses `uv sync`. Do not retain `requirements.txt` as a second mutable dependency manifest.

3. **容器运行基线 / Container runtime baseline.** Agent 与 Backend 使用专用非 root UID 运行；Backend 私有文件卷目录对该 UID 可写。为 Agent `/health` 和 Backend `/api/health` 配置 Compose healthcheck，并让 Backend 等待 Agent healthy。所有服务的 `json-file` 日志限制为每文件 10 MiB、最多 3 个文件。保留已有 `.dockerignore` 对 `.env` 的排除规则。

   Run Agent and Backend under dedicated non-root UIDs; make the Backend's private file-volume directory writable by its UID. Add Compose healthchecks for Agent `/health` and Backend `/api/health`, and make Backend wait for Agent to become healthy. Limit every service's `json-file` logs to 10 MiB per file and three files. Preserve the existing `.dockerignore` rule that excludes `.env`.

4. **文档 / Documentation.** 将 README 和 Agent 测试指南更新为 Python 3.13、`uv sync` 和 `uv run uvicorn app:app`；更新数据库迁移说明，保持改动段落中英一致。

   Update the README and Agent test guide to use Python 3.13, `uv sync`, and `uv run uvicorn app:app`; update database migration instructions and keep changed passages bilingual.

## 不在本次范围内 / Out of Scope

本次不增加 CI 自动测试、Actuator/Prometheus、运行时新服务、资源配额或组件大版本升级。已有自动化测试不在本轮执行。

This batch does not add CI test runs, Actuator/Prometheus, runtime services, resource quotas, or major component upgrades. Existing automated tests are not run in this batch.

## 验收标准 / Acceptance Criteria

- 空 PostgreSQL 数据库可由 Flyway 从 V1 初始化并执行 V2；已由现有脚本初始化的数据库可建立版本 1 基线并安全执行 V2。
- Compose 不再挂载 `infra/init.sql` 到 PostgreSQL entrypoint；Flyway 历史表记录已执行版本。
- `uv.lock` 与 `pyproject.toml` 同步，运行依赖包含 `python-dotenv`，`pytest` 仅在开发依赖组中。
- Docker Agent 构建使用锁定依赖，并以非 root 身份运行；Backend 容器以非 root 身份运行且能写入命名文件卷。
- Compose 健康检查反映 Agent 与 Backend 实际 HTTP 状态，Backend 依赖 Agent healthy；每项服务日志轮转上限为 10 MiB × 3。
- README 和 Agent 测试指南给出可工作的双语启动命令与迁移说明。
- 代码/配置差异通过编译或解析检查及 `git diff --check`；不新增或运行自动化测试。

- An empty PostgreSQL database is initialized by Flyway V1 and upgraded by V2; a database initialized by the existing scripts can be baselined at version 1 and safely receive V2.
- Compose no longer mounts `infra/init.sql` into the PostgreSQL entrypoint; Flyway history records applied versions.
- `uv.lock` is synchronized with `pyproject.toml`, runtime dependencies include `python-dotenv`, and `pytest` is only in the development dependency group.
- The Docker Agent build installs locked dependencies and runs as non-root; the Backend runs as non-root and can write to its named file volume.
- Compose healthchecks reflect actual Agent and Backend HTTP health, Backend waits for Agent healthy, and every service's log rotation is limited to 10 MiB × 3.
- The README and Agent test guide provide working bilingual startup commands and migration instructions.
- Code/configuration changes pass compile or parse checks and `git diff --check`; no automated tests are added or run.

## 约束 / Constraints

- 不加入新运行时服务或非必要运行时依赖。 / Add no runtime services or unnecessary runtime dependencies.
- 保留 PostgreSQL 17、Python 3.13 和现有 Maven 构建运行环境。 / Retain PostgreSQL 17, Python 3.13, and the current Maven build/runtime environment.
- 数据库迁移脚本及所有本次修改的说明和注释均中英双语。 / Migration scripts and all documentation/comments changed in this batch are bilingual.

## 参考 / References

- [Flyway PostgreSQL database module](https://documentation.red-gate.com/flyway/reference/database-driver-reference/postgresql-database) — PostgreSQL support is provided by a dedicated module. / PostgreSQL 支持由独立数据库模块提供。
- [Flyway baseline-on-migrate](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-baseline-on-migrate-setting) — Baseline applies to non-empty schemas without history; empty schemas run migrations normally. / 基线适用于没有历史表的非空 schema；空 schema 正常执行迁移。
- [Flyway Spring Boot integration](https://documentation.red-gate.com/flyway/reference/usage/community-plugins-and-integrations/community-plugins-and-integrations-spring-boot) — Spring Boot runs Flyway migrations at startup and manages dependency versions. / Spring Boot 可在启动时执行迁移并管理依赖版本。
- [Spring Boot database initialization](https://docs.spring.io/spring-boot/4.1/how-to/data-initialization.html) — Spring Boot 4 uses `spring-boot-starter-flyway` and a database-specific module such as `flyway-database-postgresql`. / Spring Boot 4 使用 `spring-boot-starter-flyway` 和数据库专用模块（例如 `flyway-database-postgresql`）。
- [uv locking and syncing](https://docs.astral.sh/uv/concepts/projects/sync/) — `uv.lock` records resolved versions and `uv sync --locked` rejects stale lockfiles. / `uv.lock` 记录解析后的版本，`uv sync --locked` 会拒绝过期锁文件。
- [uv in Docker](https://docs.astral.sh/uv/guides/integration/docker/) — Official Docker guidance covers locked installs, non-editable deployment installs, and layer caching. / 官方 Docker 指南说明锁定安装、部署用非 editable 安装和镜像层缓存。
