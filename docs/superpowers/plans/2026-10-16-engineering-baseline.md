# 工程基线实施计划 / Engineering Baseline Implementation Plan

> **执行方式 / Execution:** Codex 在本会话按任务顺序内联实施；不运行自动化测试。 / Execution: Codex implements tasks inline in this session; automated tests are not run.

**目标 / Goal:** 版本化数据库迁移、锁定 Python Agent 依赖、修正启动说明并强化容器默认运行方式。 / Version database migrations, lock Python Agent dependencies, correct setup instructions, and harden container defaults.

**架构 / Architecture:** Flyway 管理 PostgreSQL schema 版本；uv 管理 Python 依赖及锁文件；Compose 健康检查、非 root 用户和日志轮转完善运行基线。 / Flyway manages PostgreSQL schema versions; uv manages Python dependencies and the lockfile; Compose healthchecks, non-root users, and log rotation complete the runtime baseline.

**技术栈 / Tech Stack:** Spring Boot 4 Flyway integration, PostgreSQL, uv, Python 3.13, Docker Compose. / Spring Boot 4 Flyway 集成、PostgreSQL、uv、Python 3.13、Docker Compose。

**规格 / Spec:** [工程基线设计 / Engineering Baseline Design](../specs/2026-10-16-engineering-baseline-design.md)

## 全局约束 / Global Constraints

- 本次修改的文档、SQL 注释和配置注释均中英双语。 / Documentation, SQL comments, and configuration comments changed in this batch are bilingual.
- 不增加运行时服务，不升级主要组件版本。 / Add no runtime services or major component upgrades.
- 不新增或运行自动化测试；执行编译、解析、锁文件一致性与差异检查。 / Do not add or run automated tests; perform compilation, parsing, lockfile-consistency, and diff checks.
- 旧数据库基线版本为 1，工作流升级迁移必须幂等。 / The existing database baseline is version 1, and the workflow upgrade migration must be idempotent.

## 评审重点 / Review Focus

- 空库必须执行 V1 和 V2，已初始化的非空库必须安全基线并执行 V2。 / Empty databases must run V1 and V2; initialized non-empty databases must baseline safely and run V2.
- Flyway 迁移期间不能再由 PostgreSQL entrypoint 重复执行初始化 SQL。 / PostgreSQL entrypoint must not duplicate initialization while Flyway runs migrations.
- Docker 构建必须使用已提交锁文件且生产镜像不安装 pytest。 / Docker builds must use the committed lockfile and exclude pytest from the production image.
- 非 root Backend 必须可写挂载的文件目录，健康检查端点必须在安全配置中可访问。 / Non-root Backend must write to the mounted file directory, and healthcheck endpoints must be reachable under security configuration.
- 健康检查依赖要与启动顺序相符；日志限制要应用于四个 Compose 服务。 / Health dependencies must match startup order, and log limits must apply to all four Compose services.

---

### Task 1: Adopt Flyway / 任务 1：接入 Flyway

**Files / 文件：**
- Modify / 修改: `backend/pom.xml`
- Create / 新建: `backend/src/main/resources/db/migration/V1__initial_schema.sql`
- Create / 新建: `backend/src/main/resources/db/migration/V2__excel_workflows.sql`
- Remove after conversion / 转换后删除: `infra/init.sql`, `infra/migrations/001_excel_workflows.sql`
- Modify / 修改: `backend/src/main/resources/application.yml`, `docker-compose.yml`
- Modify / 修改: `README.md`, `docs/excel-saas-integration.md`, `docs/excel-workflows-api.md`, `docs/learning-guide.md`

- [x] Move the current base schema/seeds into V1 and the existing idempotent workflow upgrade into V2; include Spring Boot's Flyway starter and PostgreSQL module without overriding managed versions. / 将现有基础 schema/seed 移入 V1，将现有幂等工作流升级迁移移入 V2；加入 Spring Boot Flyway starter 与 PostgreSQL 模块，不覆盖依赖管理的版本。
- [x] Configure baseline-on-migrate at version 1 and remove the PostgreSQL init-script bind mount; verify empty/new and existing-volume paths by reviewing migration order and SQL idempotence. / 配置版本 1 的 baseline-on-migrate 并移除 PostgreSQL 初始化脚本挂载；通过检查迁移顺序和 SQL 幂等性复核空库/新库与既有数据卷路径。
- [x] Update database setup docs in bilingual paired sections and run `git diff --check`. / 以中英双语对应段落更新数据库启动文档并运行 `git diff --check`。

### Task 2: Lock Python Dependencies / 任务 2：锁定 Python 依赖

**Files / 文件：**
- Create / 新建: `agent-service/pyproject.toml`, `agent-service/uv.lock`
- Modify / 修改: `agent-service/Dockerfile`, `agent-service/.dockerignore`
- Remove / 删除: `agent-service/requirements.txt`
- Modify / 修改: `README.md`, `docs/test-v1.6.md`

- [x] Declare Python 3.13 and all current runtime dependencies in `pyproject.toml`; add `python-dotenv` to runtime dependencies and `pytest` to the development group. / 在 `pyproject.toml` 声明 Python 3.13 和当前运行依赖；将 `python-dotenv` 加入运行依赖，将 `pytest` 放入开发依赖组。
- [x] Generate and verify `uv.lock`; make the Docker image install with `uv sync --locked --no-dev` and exclude local virtualenv/cache files from the build context. / 生成并校验 `uv.lock`；Docker 镜像使用 `uv sync --locked --no-dev` 安装依赖，并从构建上下文排除本地虚拟环境/缓存文件。
- [x] Update local setup commands to `uv sync` and `uv run uvicorn app:app --host 0.0.0.0 --port 8000`; describe the development test dependency without running tests. / 将本地启动命令更新为 `uv sync` 和 `uv run uvicorn app:app --host 0.0.0.0 --port 8000`；说明开发测试依赖但不执行测试。

### Task 3: Harden Container Runtime / 任务 3：强化容器运行基线

**Files / 文件：**
- Modify / 修改: `agent-service/Dockerfile`
- Modify / 修改: `backend/Dockerfile`
- Modify / 修改: `docker-compose.yml`

- [x] Run both application images as dedicated non-root users; ensure Backend's `/app/data/files` directory and named-volume initialization are writable by its UID. / 两个应用镜像均使用专用非 root 用户运行；确保 Backend 的 `/app/data/files` 目录和命名卷初始化后可由其 UID 写入。
- [x] Add Agent and Backend healthchecks for `/health` and `/api/health`; make Backend depend on Agent healthy. / 为 Agent `/health` 与 Backend `/api/health` 添加健康检查；让 Backend 等待 Agent healthy。
- [x] Add `json-file` log rotation of 10 MiB and 3 files to all services; review the final Compose structure. / 为所有服务添加 `json-file` 日志轮转，单文件 10 MiB、最多 3 个文件；检查最终 Compose 结构。

### Task 4: Final Verification / 任务 4：最终验证

- [x] Compile Backend with tests skipped, validate Python syntax and uv lock synchronization, inspect changed comments/docs for bilingual alignment, and run `git diff --check`. / 跳过测试编译 Backend、校验 Python 语法和 uv 锁文件同步、检查本次修改注释/文档的中英一致性，并运行 `git diff --check`。
- [x] Commit the batch after all checks pass; do not push. / 所有检查通过后提交本批次；不推送。
