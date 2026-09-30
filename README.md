# Low-Cost AI Agent V1.6 — Excel SaaS Integration

你的低成本 AI Agent / Multi-Agent 学习与产品底座。

## 技术栈

- OpenCode：AI Coding
- DeepSeek Harness：可选 Agent Harness / 开发自动化
- Python 3.13 + uv
- LangGraph：产品 Agent / Multi-Agent
- Ollama：本地 LLM
- Spring Boot 4.1.1 + Java 21
- PostgreSQL 17
- Redis 8
- Docker Compose
- OpenAI-compatible Remote LLM

## 最重要的边界

DeepSeek Harness 不替代 LangGraph。

```text
OpenCode          -> 帮你开发
DeepSeek Harness  -> Agent 执行环境/开发自动化
LangGraph         -> 产品 Multi-Agent 编排
Tool Registry     -> 受控业务能力
Spring Boot       -> 业务系统
```

DeepSeek Harness 当前为 Developer Preview，因此本项目只做可选集成。

## Excel SaaS 合并目标

此仓库作为唯一技术底座，承接 `excel-ai-saas-v2` 的前端、注册登录、用户文件、异步任务、套餐/额度和 Excel 操作业务。业务由 Spring Boot 提供 API 和 Java Tool Registry；LangGraph 负责 Agent 编排；PostgreSQL 保存业务数据，Redis 保存任务进度和会话状态。Excel 文件通过 V1.6 LangGraph → Java Tool Registry 流程处理；不引入 V2 的 MySQL 或独立 Java AgentExecutor。

迁移说明见 `docs/excel-saas-integration.md`。PostgreSQL 初始化脚本包含 SaaS 业务表及 FREE/BASIC/PRO 初始套餐。

学习代码的推荐阅读顺序、各模块职责、配置变量和数据边界见 [docs/learning-guide.md](docs/learning-guide.md)。源码和可注释配置文件内也补充了中英双语说明。

## 启动

### Docker 一键启动（推荐）

在项目根目录运行：

```powershell
docker compose up --build -d
docker compose ps
```

这会启动 PostgreSQL 17、Redis 8、LangGraph Agent 和 Spring Boot 后端（Maven 3.9 / JDK 21）。产品页面在 <http://localhost:8080/>，首次使用先注册，再上传 Excel 并输入自然语言处理要求。服务地址：

- 后端健康检查：<http://localhost:8080/api/health>
- Agent 健康检查：<http://localhost:8000/health>
- Java Tool Registry：由 Agent 服务使用内部共享令牌访问，不作为公开 API 使用

首次启动前将 `.env.example` 复制为根目录 `.env`，按需填写 LLM 设置与密钥。使用 `LLM_PROVIDER=ollama` 时，需要宿主机 Ollama 正在运行并准备好模型；使用 `LLM_PROVIDER=remote` 时，配置远程 OpenAI 兼容服务。停止服务：`docker compose down`（保留数据库卷）。

如需远程 OpenAI 兼容模型，在根目录 `.env` 设置 `LLM_PROVIDER=remote`、`REMOTE_LLM_BASE_URL`、`REMOTE_LLM_API_KEY` 和 `REMOTE_LLM_MODEL`，然后运行 `docker compose up --build -d` 使配置生效。API Key 不要填入 `.env.example` 或提交到版本库。

### 1. 基础设施

```powershell
docker compose up -d postgres redis
docker compose ps
```

### 2. Ollama

```powershell
ollama pull qwen3:8b
ollama list
```

### 3. Python Agent

```powershell
cd agent-service
uv python install 3.13
uv venv --python 3.13
.venv\Scripts\activate
uv pip install -r requirements.txt
copy .env.example .env
python app.py
```

### 4. Spring Boot

```powershell
cd backend
mvn spring-boot:run
```

### 5. DeepSeek Harness

单独 PowerShell：

```powershell
cd harness
.\run.ps1
```

或：

```powershell
npx @deepseek-ai/dsh web --no-open
```

默认 Web UI：

http://127.0.0.1:3080

## SaaS Agent 流程

页面提交任务后，Spring Boot 将任务发送给 LangGraph；LangGraph 通过受内部令牌保护的 Java Tool Registry 调用 `read_excel`、`filter`、`sort`、`top`、`export_excel`。

上传文件保存在 `excel_files` Docker 卷；PostgreSQL / Redis 卷在普通 `docker compose down` 时保留。更新 `.env` 后用 `docker compose up --build -d` 重载配置。


# V1.6 — Java Tool Registry 正式版

V1.6 的核心变化：**Python 不再拥有业务 Tool Registry，Java Spring Boot 成为唯一受控工具边界。**

## 组件职责

| 组件 | 职责 |
|---|---|
| OpenCode | AI 编程 |
| DeepSeek Harness | 开发者自动化 / Agent Harness |
| LangGraph | Product Agent / Multi-Agent 编排 |
| Spring Boot | 业务后端 + Java Tool Registry |
| `@AgentTool` | Java 工具声明 |
| ToolRegistry | 自动发现工具 |
| ToolExecutionService | 参数校验、权限、Timeout、执行 |
| AuditService | 工具调用审计 |
| PostgreSQL | 持久化 |
| Redis | Session / Task / Cache |
| Ollama | 本地低成本模型 |

## V1.6 核心调用链

```text
用户
  ↓
Spring Boot
  ↓
LangGraph
  ↓
LLM 决定调用 getUser
  ↓ HTTP
Java Tool Registry
  ↓
权限检查
  ↓
参数校验
  ↓
Timeout
  ↓
Java @AgentTool
  ↓
Audit
  ↓
返回结果
  ↓
LangGraph
  ↓
LLM 最终回答
```

## Java 中增加一个 Agent Tool

```java
@AgentTool(
    name = "getUser",
    description = "Get user by ID",
    permission = "user:read",
    timeoutMs = 3000
)
public Map<String, Object> getUser(
    @ToolParam(name = "userId", description = "User ID")
    Integer userId
) {
    // 这里以后放真正的 UserService
}
```

**重点：Agent 不需要知道你的数据库表、MyBatis、Redis Key、文件路径。**

它只能看到 Tool Schema。

## V1.6 新增

- Java Annotation Tool
- Spring Bean 自动发现
- Tool Schema 自动生成
- 参数必填/未知参数/类型校验
- Principal + Permission
- Tool Timeout
- Audit Log
- LangGraph → Java Tool Registry HTTP
- `getUser`
- `getOrder`
- `slowTool` Timeout Demo
- V1.6 完整测试文档

详细架构：

`docs/v1.6-architecture.md`

测试：

`docs/test-v1.6.md`

Excel SaaS business integration target and migration plan:

`docs/excel-saas-integration.md`
