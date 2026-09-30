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

## 8. Start Python Agent

```powershell
cd agent-service
uv venv --python 3.12
.venv\Scripts\activate
uv pip install -r requirements.txt
copy .env.example .env
python app.py
```

## 9. Test Agent

```powershell
curl -X POST http://localhost:8000/agent/chat `
  -H "Content-Type: application/json" `
  -d '{"message":"查询用户10001的信息"}'
```

The intended path is:

LLM -> LangGraph tool call -> HTTP -> Java Tool Registry -> getUser -> result -> LLM.
