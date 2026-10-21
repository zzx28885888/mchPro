# V1.6 Test Guide

## 1. Start infra

```powershell
docker compose up -d postgres redis
```

## 2. Start Java

```powershell
cd backend
mvn spring-boot:run
```

## 3. Check tool discovery

```powershell
curl http://localhost:8080/internal/agent/tools
```

Expected tools:

- getUser
- getOrder
- slowTool

## 4. Execute a valid tool

```powershell
curl -X POST http://localhost:8080/internal/agent/tools/getUser `
  -H "Content-Type: application/json" `
  -d '{"principal":"langgraph","arguments":{"userId":10001}}'
```

## 5. Test parameter validation

Missing parameter:

```powershell
curl -X POST http://localhost:8080/internal/agent/tools/getUser `
  -H "Content-Type: application/json" `
  -d '{"principal":"langgraph","arguments":{}}'
```

Unknown parameter:

```powershell
curl -X POST http://localhost:8080/internal/agent/tools/getUser `
  -H "Content-Type: application/json" `
  -d '{"principal":"langgraph","arguments":{"userId":10001,"hack":"x"}}'
```

## 6. Test permission

```powershell
curl -X POST http://localhost:8080/internal/agent/tools/getUser `
  -H "Content-Type: application/json" `
  -d '{"principal":"unknown-agent","arguments":{"userId":10001}}'
```

Expected:

`PERMISSION_DENIED`

## 7. Test timeout

The tool timeout is 1000ms:

```powershell
curl -X POST http://localhost:8080/internal/agent/tools/slowTool `
  -H "Content-Type: application/json" `
  -d '{"principal":"langgraph","arguments":{"sleepMs":3000}}'
```

Expected:

`TOOL_TIMEOUT`

## 8. Start Python Agent / 启动 Python Agent

Use Python 3.13 and `uv` from the `agent-service` directory. `uv sync` installs the project dependencies from the committed lockfile; `pytest` is included only in the development dependency group.

在 `agent-service` 目录使用 Python 3.13 和 `uv`。`uv sync` 根据已提交的锁文件安装项目依赖；`pytest` 仅包含在开发依赖组中。

```powershell
cd agent-service
uv python install 3.13
uv sync
copy .env.example .env
uv run uvicorn app:app --host 0.0.0.0 --port 8000
```

## 9. Test Agent

Set `AGENT_INTERNAL_TOKEN` in the shell to the same secret configured in `agent-service/.env` before starting the Agent. The `/agent/chat` endpoint now requires this token.

启动 Agent 前，请将 shell 中的 `AGENT_INTERNAL_TOKEN` 设置为与 `agent-service/.env` 相同的密钥。`/agent/chat` 接口现在要求提供此令牌。

```powershell
curl.exe -X POST http://localhost:8000/agent/chat `
  -H "X-Agent-Token: $env:AGENT_INTERNAL_TOKEN" `
  -H "Content-Type: application/json" `
  -d '{"message":"查询用户10001的信息"}'
```

The intended path is / 调用链路如下：

LLM -> LangGraph tool call -> HTTP -> Java Tool Registry -> getUser -> result -> LLM.
