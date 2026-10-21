# API Hardening Implementation Plan / API 加固实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **面向代理执行者：** 必须使用 `superpowers:executing-plans` 按任务逐项实施。本计划使用 `- [ ]` 跟踪步骤。

**Goal:** Close the API validation, error-handling, health, rate-limit, and Excel-upload security gaps. / **目标：** 修复 API 校验、异常处理、健康检查、速率限制和 Excel 上传安全缺口。

**Architecture:** Reuse Spring Validation and existing Redis/PostgreSQL services. Keep errors consistent and non-sensitive, use Redis atomic fixed windows for request throttling, and validate XLSX content and decompression boundaries before persistent storage. / **架构：** 复用 Spring Validation 与现有 Redis/PostgreSQL 服务；统一错误响应并避免泄露敏感信息；使用 Redis 原子固定窗口限流；在持久化前校验 XLSX 内容与解压边界。

**Tech Stack:** Spring Boot 4, Spring Security, Redis/Lua, PostgreSQL, EasyExcel, Apache POI 5.5.1. / **技术栈：** Spring Boot 4、Spring Security、Redis/Lua、PostgreSQL、EasyExcel、Apache POI 5.5.1。

**Spec:** [API Hardening Design / API 加固设计](../specs/2026-10-16-api-hardening-design.md)

## Global Constraints / 全局约束

- 新增或修改的维护文档和代码注释必须中英双语。 / All new or modified maintained documentation and code comments must be bilingual.
- 不引入新的运行时服务；Redis 不可用时，受保护写入操作失败关闭并返回 503。 / Add no runtime service; protected writes fail closed with 503 when Redis is unavailable.
- 使用 socket peer IP 并忽略未经验证的 `X-Forwarded-For`；反向代理后的客户端共用一个限流桶。 / Use the socket peer IP and ignore unverified `X-Forwarded-For`; clients behind a reverse proxy share a rate-limit bucket.
- Apache POI 的 `poi` 与 `poi-ooxml` 直接依赖固定为 5.5.1；上传仅接受有效 `.xlsx` OOXML 文件。 / Pin the direct Apache POI `poi` and `poi-ooxml` dependencies to 5.5.1; accept only valid `.xlsx` OOXML files.
- 不运行或新增自动化测试；本轮使用代码审查、配置检查和 `git diff --check` 验证。 / Do not add or run automated tests; verify this batch with code review, configuration checks, and `git diff --check`.

## Review Focus / 评审重点

- Redis 计数并发首次写入时窗口 TTL 必须原子设置，避免无过期键。 / Concurrent first increments must set the Redis window TTL atomically to prevent permanent keys.
- 未知客户端 IP、IPv6 地址及本地代理不能导致限流键碰撞或伪造绕过。 / Unknown client IPs, IPv6 addresses, and local proxies must not cause rate-key collisions or spoofing bypasses.
- 上传的有效 XLSX 可能包含高压缩率或超大条目；解析边界必须拒绝此类文件。 / A valid XLSX may contain highly compressed or oversized entries; parser limits must reject them.
- 写入磁盘后若数据库插入失败，孤儿文件应清理且清理错误不能覆盖原始错误。 / If database insertion fails after disk storage, remove the orphan file without masking the original error.
- Validation、`ResponseStatusException` 和业务异常状态必须稳定，内部异常文本不得进入响应。 / Validation, `ResponseStatusException`, and business exception statuses must remain stable, and internal exception text must not reach responses.

---

### Task 1: Validation and Error Mapping / 任务 1：请求校验与异常映射

**Files / 文件：**
- Modify / 修改: `backend/src/main/java/com/excelai/auth/AuthController.java`
- Modify / 修改: `backend/src/main/java/com/excelai/common/GlobalExceptionHandler.java`

- [x] Enable `@Valid` on both request bodies; ensure validation errors return 400 and preserve existing `@Email`, `@NotBlank`, and password length constraints. / 为两个请求体启用 `@Valid`；确保校验失败返回 400，并保留现有邮箱、非空及密码长度约束。
- [x] Add explicit handlers for `ResponseStatusException` and validation errors; log unexpected exceptions and return a generic 500 body without `e.getMessage()`. / 显式处理 `ResponseStatusException` 与参数校验错误；记录未预期异常日志并返回不包含 `e.getMessage()` 的通用 500 响应。
- [x] Review status mapping and bilingual changed comments; `git diff --check` passed. / 检查状态码映射和本次修改注释的中英双语；`git diff --check` 已通过。

### Task 2: Accurate Health Probe / 任务 2：准确健康探测

**Files / 文件：**
- Modify / 修改: `backend/src/main/java/com/example/agent/controller/HealthController.java`

- [x] Probe PostgreSQL and Redis independently; return 200 only when both are healthy, otherwise return 503 with per-dependency state. / 独立探测 PostgreSQL 与 Redis；仅在两者均健康时返回 200，否则返回 503 并分别给出依赖状态。
- [x] Close the acquired Redis connection on success and failure paths; retain a failed dependency result rather than swallowing it into `UP`. / 在成功和失败路径都关闭 Redis 连接；探测失败必须如实标记，不能被吞掉并报告为 `UP`。
- [x] Review failure paths and bilingual changed comments; `git diff --check` passed. / 检查失败路径和本次修改注释的中英双语；`git diff --check` 已通过。

### Task 3: Atomic Rate Limits / 任务 3：原子速率限制

**Files / 文件：**
- Create / 新建: `backend/src/main/java/com/excelai/config/RateLimitProperties.java`
- Create / 新建: `backend/src/main/java/com/excelai/common/RateLimitService.java`
- Create / 新建: `backend/src/main/java/com/excelai/common/RateLimitExceededException.java`
- Create / 新建: `backend/src/main/java/com/excelai/common/RateLimitInterceptor.java`
- Create / 新建: `backend/src/main/java/com/excelai/config/RateLimitWebConfiguration.java`
- Modify / 修改: `backend/src/main/java/com/excelai/common/GlobalExceptionHandler.java`
- Modify / 修改: `backend/src/main/java/com/excelai/config/AppConfig.java`
- Modify / 修改: `backend/src/main/resources/application.yml`
- Modify / 修改: `docker-compose.yml`
- Modify / 修改: `.env.example`, `README.md`

- **Consumes / 使用：** Task 1's uniform error response and `ApiException` mapping. / 任务 1 的统一错误响应与 `ApiException` 映射。
- **Produces / 提供：** `RateLimitProperties.Policy(maxRequests, window)` and `RateLimitService.enforce(category, subject, policy)`, used by a Spring MVC interceptor after authentication. / 提供 `RateLimitProperties.Policy(maxRequests, window)` 和 `RateLimitService.enforce(category, subject, policy)`，由认证过滤器之后的 Spring MVC 拦截器调用。

- [x] Configure defaults: registration 5 requests/IP/hour, login 10 requests/IP/15 minutes, task preview plus creation 10 requests/user/minute; keep all windows and limits environment-configurable. / 设置默认值：注册每 IP 每小时 5 次、登录每 IP 每 15 分钟 10 次、任务预览与创建合计每用户每分钟 10 次；所有窗口和次数均可通过环境变量配置。
- [x] Register `RateLimitProperties` in `AppConfig`; implement `enforce(category, subject, policy)` with a single Redis Lua fixed-window increment that atomically initializes expiry and returns count plus remaining TTL; hash the subject in Redis keys. / 在 `AppConfig` 注册 `RateLimitProperties`；实现 `enforce(category, subject, policy)`，使用单个 Redis Lua 固定窗口脚本原子初始化过期时间并返回计数与剩余 TTL；Redis 键中的主体标识使用哈希值。
- [x] Intercept only POST `/api/auth/register`, `/api/auth/login`, `/api/tasks/preview`, and `/api/tasks`; use socket peer IP for auth and the verified numeric principal for both task routes, ignoring forwarded headers. / 仅拦截 POST `/api/auth/register`、`/api/auth/login`、`/api/tasks/preview` 和 `/api/tasks`；认证接口使用 socket peer IP，两个任务路由使用已验证的数字主体，并忽略转发头。
- [x] Return 429 with `Retry-After` when exceeded and 503 when Redis is unavailable; `git diff --check` passed. / 超限时返回含 `Retry-After` 的 429，Redis 不可用时返回 503；`git diff --check` 已通过。
- [x] Map `RateLimitExceededException` to 429 and set `Retry-After` to the remaining window in seconds. / 将 `RateLimitExceededException` 映射为 429，并将 `Retry-After` 设为当前窗口剩余秒数。
- [x] Document the defaults and proxy-IP behavior bilingually; inspect key construction and configuration; `git diff --check` passed. / 以中英双语说明默认限制和代理 IP 行为；检查键构造与配置；`git diff --check` 已通过。

### Task 4: Safe Excel Upload / 任务 4：安全 Excel 上传

**Files / 文件：**
- Modify / 修改: `backend/pom.xml`
- Create / 新建: `backend/src/main/java/com/excelai/config/ExcelSecurityConfiguration.java`
- Modify / 修改: `backend/src/main/java/com/excelai/file/FileController.java`
- Modify / 修改: `backend/src/main/java/com/excelai/file/FileStorageService.java`
- Modify / 修改: `backend/src/main/resources/static/index.html`
- Use / 使用: `backend/src/main/java/com/excelai/user/UserRepository.java` and `backend/src/main/java/com/excelai/plan/PlanRepository.java` to resolve the authenticated user's current plan.
- Modify / 修改: `README.md`

- [x] Pin `org.apache.poi:poi` and `org.apache.poi:poi-ooxml` to 5.5.1, overriding EasyExcel's transitive POI versions; configure `ZipSecureFile` minimum inflate ratio to 0.01 and maximum single entry size to 64 MiB. / 将 `org.apache.poi:poi` 和 `org.apache.poi:poi-ooxml` 固定为 5.5.1，覆盖 EasyExcel 的传递版本；配置 `ZipSecureFile` 最小解压比例 0.01、单条目最大 64 MiB。
- [x] Stream-scan multipart content before permanent storage; require OOXML package entries and reject more than 10,000 entries or actual aggregate expansion above 256 MiB. / 在永久落盘前流式扫描 multipart 内容；要求包含 OOXML 包关键条目，并拒绝条目数超过 10,000 或实际总解压大小超过 256 MiB 的文件。
- [x] Enforce a non-empty `.xlsx` filename and the authenticated user's current `maxFileSize` before calling the storage service. / 调用存储服务前校验 `.xlsx` 文件名非空，并按已认证用户当前套餐 `maxFileSize` 限制文件大小。
- [x] Delete partial files when transfer fails; if metadata insertion fails after storage, delete the just-created file and rethrow the original exception. / 文件传输失败时清理不完整文件；文件落盘后若元数据插入失败，删除刚创建的文件并重新抛出原异常。
- [x] Restrict the UI file picker and label to valid `.xlsx` files only. / 将页面文件选择器和提示同步限制为仅支持有效 `.xlsx` 文件。
- [x] Document upload rules and POI safety limits bilingually; inspect dependency configuration and cleanup paths; `git diff --check` passed. / 以中英双语说明上传规则和 POI 安全限制；检查依赖配置及清理路径；`git diff --check` 已通过。
