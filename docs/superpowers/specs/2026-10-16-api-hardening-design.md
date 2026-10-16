# API Hardening Design / API 加固设计

## 目标 / Goal

关闭审计确认的请求校验、异常响应、健康检查、滥用控制和上传校验缺口，同时保持现有 API 形状、用户归属校验和 Redis/PostgreSQL 架构。 / Close the confirmed gaps in request validation, error responses, health checks, abuse controls, and upload validation while preserving existing API shapes, ownership checks, and the Redis/PostgreSQL architecture.

## 现状 / Current State

- 注册与登录请求 DTO 声明了 Bean Validation 注解，但 Controller 未使用 `@Valid`，约束不会执行。 / Registration and login DTOs declare Bean Validation constraints, but the Controller does not use `@Valid`, so those constraints are not applied.
- 全局兜底处理器把所有未处理异常转为 500，并将异常消息返回客户端；预期框架状态异常可能被吞掉。 / The global fallback handler converts all unhandled exceptions to 500 and returns their messages; expected framework status exceptions may be swallowed.
- `/api/health` 固定返回 `UP`，数据库异常会冒泡，Redis 连接未显式释放。 / `/api/health` always reports `UP`, database failures bubble up, and Redis connections are not explicitly released.
- 公开认证和任务创建入口没有短时速率限制。 / Public authentication and task-creation endpoints have no short-window rate limits.
- 上传仅检查空文件；套餐大小限制延迟到任务阶段，文件类型及 ZIP 解压风险没有在上传边界检查。 / Upload checks only for empty content; plan size limits are deferred until task creation, and file type and ZIP expansion risks are not checked at the upload boundary.

## 设计 / Design

### 1. 请求校验与异常映射 / Request Validation and Error Mapping

- 为注册、登录请求参数启用 `@Valid`，对无效邮箱、空值及 8–72 字符密码返回 400。 / Enable `@Valid` for registration and login requests; return 400 for invalid email, blank values, and passwords outside 8–72 characters.
- 全局处理器显式映射 `ApiException`、`ResponseStatusException`、Bean Validation 错误和未处理异常。保留明确的业务错误消息与 HTTP 状态；意外异常记录服务端日志并仅返回通用 500 响应，不泄露内部异常文本。 / The global handler explicitly maps `ApiException`, `ResponseStatusException`, Bean Validation failures, and unexpected exceptions. Preserve intentional business messages and HTTP statuses; log unexpected exceptions server-side and return only a generic 500 response without internal exception text.

### 2. 依赖健康检查 / Dependency Health Checks

- PostgreSQL 和 Redis 均成功探测时返回 200 与 `UP`；任一探测失败时返回 503 与 `DOWN`，并分别报告依赖状态。 / Return 200 and `UP` only when both PostgreSQL and Redis probes succeed; return 503 and `DOWN` when either probe fails, reporting each dependency separately.
- 显式关闭 Redis connection，且探测异常不能被伪装成健康。 / Explicitly close Redis connections and never mask probe failures as healthy.

### 3. Redis 速率限制 / Redis Rate Limiting

- 复用 Redis，不引入新的运行时服务。使用原子计数与过期时间实现固定窗口；键包含路由类别和可信主体标识，不能直接拼接未经规范化的任意输入。 / Reuse Redis without adding a runtime service. Use atomic counters with expiry for fixed windows; keys include the route category and a trusted subject identifier, never raw unnormalized input.
- 为注册、登录按客户端 IP 限流；为任务预览和创建按已认证用户共用限流窗口。超过阈值返回 429 与 `Retry-After`。阈值通过配置提供并有保守默认值；Redis 不可用时受保护操作失败关闭并返回 503。 / Rate-limit registration and login by client IP; task previews and creation share a per-user limit window. Return 429 with `Retry-After` when over limit. Configure thresholds with conservative defaults; fail closed with 503 for protected operations when Redis is unavailable.
- 使用 socket peer IP 作为公开接口主体；忽略未经可信代理链验证的 `X-Forwarded-For`。部署在反向代理后时，同一代理后的客户端会共享限流桶，直到后续加入可信代理配置。 / Use the socket peer IP as the public-endpoint subject; ignore `X-Forwarded-For` unless a trusted proxy chain is configured. Clients behind the same proxy share a rate-limit bucket until trusted-proxy configuration is added later.

### 4. Excel 上传边界 / Excel Upload Boundary

- 上传时读取当前用户套餐并立即执行 `maxFileSize` 限制；拒绝空文件、空文件名、非 `.xlsx` 文件和不符合 OOXML ZIP 特征的内容。 / Read the current user's plan and enforce `maxFileSize` at upload time; reject empty files, missing filenames, non-`.xlsx` files, and content that does not match OOXML ZIP characteristics.
- 将页面文件选择器同步限制为 `.xlsx`，避免客户端提示与服务端接受格式不一致。 / Restrict the page file picker to `.xlsx` so its accepted format matches the server.
- 将 Apache POI 的 `poi` 和 `poi-ooxml` 显式提升并固定到 `5.5.1`，覆盖 EasyExcel 的传递依赖，避免依赖树继续解析到存在已知 OOXML 安全问题的旧版 POI。 / Explicitly upgrade and pin Apache POI's `poi` and `poi-ooxml` modules to `5.5.1`, overriding EasyExcel's transitive dependencies so the dependency tree cannot resolve to an older POI release with known OOXML security issues.
- 在 POI 解析器设置最小解压比例 `0.01`、单条目解压上限 `64 MiB`，并在解析前拒绝条目数超过 `10,000` 或实际累计解压大小超过 `256 MiB` 的文件。限制值集中配置并记录在双语文档中。 / Configure a minimum inflate ratio of `0.01` and a `64 MiB` per-entry expanded-size limit in POI; before parsing, reject archives with more than `10,000` entries or actual aggregate expanded size above `256 MiB`. Keep limits centralized and document them bilingually.
- 先完成所有轻量校验，再持久化文件和元数据；元数据写入失败时删除刚写入的文件。 / Complete lightweight validation before persisting file and metadata; delete the stored file if metadata insertion fails.

## 范围外 / Out of Scope

本规格不处理任务与额度的数据库原子性、队列溢出和任务恢复、Python/Java 超时协调、CI/依赖锁、Flyway、可观测性、进度更新、JWT 刷新撤销及列表 API；这些留给后续批次。 / This spec does not cover task/quota database atomicity, queue overflow and recovery, Python/Java timeout coordination, CI/dependency locking, Flyway, observability, progress updates, JWT refresh/revocation, or list APIs; those remain for later batches.

## 验收标准 / Acceptance Criteria

- 无效认证输入返回 400；无效凭证仍返回 401；业务 4xx 不被转换为 500。 / Invalid authentication input returns 400; invalid credentials remain 401; business 4xx responses are not converted to 500.
- 意外服务端异常写入日志并返回不含内部错误文本的通用 500。 / Unexpected server errors are logged and return a generic 500 without internal error text.
- 健康检查对 PostgreSQL/Redis 的真实状态返回正确 HTTP 状态，并在所有路径释放 Redis 连接。 / Health checks return HTTP statuses matching actual PostgreSQL/Redis availability and release Redis connections on every path.
- 认证与任务提交在配置窗口内被限流；代理头不能由不可信客户端伪造；超过限制返回 429，Redis 失效返回 503。 / Authentication and task submission are rate-limited within configured windows; untrusted clients cannot spoof proxy headers; over-limit requests get 429 and Redis failures get 503.
- 超套餐大小、错误扩展名、伪装内容及危险 ZIP 上传在写入应用私有永久存储或数据库前被拒绝；元数据写入失败不会遗留永久文件。 / Oversized, wrong-extension, disguised-content, and unsafe ZIP uploads are rejected before writing to private permanent storage or the database; metadata failures do not leave permanent orphan files.

## 实施批次 / Implementation Batches

后续全量优化分三批：第一批实现本规格；第二批处理任务原子性、可靠派发/恢复、超时与进度；第三批处理依赖锁定、CI、Flyway、可观测性和容器运行基线。每批独立计划、验证并提交。 / The remaining optimization work proceeds in three batches: Batch 1 implements this spec; Batch 2 addresses task atomicity, reliable dispatch/recovery, timeouts, and progress; Batch 3 addresses dependency locking, CI, Flyway, observability, and container runtime hardening. Each batch gets its own plan, verification, and commit.

## 技术依据 / Technical References

- Apache POI 下载页列出版本 `5.5.1`；项目公告说明 CVE-2025-31672 在 `poi-ooxml` `5.4.0` 起修复。 / The Apache POI download page lists version `5.5.1`; project news states CVE-2025-31672 is fixed starting with `poi-ooxml` `5.4.0`. [Release downloads](https://poi.apache.org/download.html), [project news](https://poi.apache.org/index.html?lang=eng)
- Apache POI `ZipSecureFile` API 提供最小解压比例和单条目大小限制；POI 安全指南建议对不可信 Office 文档增加隔离和资源边界。 / Apache POI's `ZipSecureFile` API provides minimum inflate-ratio and per-entry size limits; its security guidance recommends additional isolation and resource limits for untrusted Office documents. [API reference](https://poi.apache.org/apidocs/dev/org/apache/poi/openxml4j/util/ZipSecureFile.html), [security guidance](https://poi.apache.org/security.html)
