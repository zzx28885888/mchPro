"""中文：LangGraph 服务的 HTTP 入口。English: HTTP entry point for the LangGraph service."""

import os
import hmac
import logging
from uuid import UUID

from dotenv import load_dotenv
load_dotenv()

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel

from agent_graph import build_graph
from java_tool_client import JavaToolRegistryClient
from java_tool_client import request_context
from usage_metering import aggregate_usage

INTERNAL_TOKEN = os.getenv("AGENT_INTERNAL_TOKEN", "")
if (not INTERNAL_TOKEN or INTERNAL_TOKEN != INTERNAL_TOKEN.strip()
        or INTERNAL_TOKEN.lower().startswith("replace-with-")
        or INTERNAL_TOKEN.lower() == "local-agent-token-change-me"):
    raise RuntimeError("AGENT_INTERNAL_TOKEN must be set to a non-placeholder secret")
INTERNAL_TOKEN_BYTES = INTERNAL_TOKEN.encode("utf-8")
logger = logging.getLogger(__name__)


def require_agent_token(x_agent_token: str | None = Header(default=None)) -> None:
    supplied = x_agent_token.encode("utf-8") if x_agent_token is not None else b""
    if not hmac.compare_digest(INTERNAL_TOKEN_BYTES, supplied):
        raise HTTPException(status_code=401, detail="Unauthorized")

# 中文：构建一次图以复用模型客户端和节点定义；业务状态仍按每次请求单独传入。
# English: Build the graph once to reuse model clients and node definitions; request state is passed separately per call.
app = FastAPI(title="Low Cost AI Agent V1.6")
graph = build_graph()
java_client = JavaToolRegistryClient()


class ChatRequest(BaseModel):
    """中文：执行 UUID 和 context 均由后端提供；context 不是模型工具参数。
    English: The backend provides the execution UUID and context; context is not a model tool argument.
    """
    message: str
    execution_id: UUID
    context: dict = {}


@app.get("/health")
async def health():
    return {"status": "UP", "version": "v1.6"}


@app.get("/tools", dependencies=[Depends(require_agent_token)])
async def tools():
    # 中文：只返回 Java 注册表定义；此路由不执行工具。
    # English: Returns Java registry definitions only; this route does not execute tools.
    return {"tools": await java_client.list_tools()}


@app.post("/agent/chat", dependencies=[Depends(require_agent_token)])
async def chat(req: ChatRequest):
    # 中文：ContextVar 让同一请求的并发工具协程读取到相同任务身份；finally 防止复用任务残留上下文。
    # English: ContextVar propagates this request's identity to tool coroutines; finally prevents context leakage between reused tasks.
    token = request_context.set({
        **req.context,
        "executionId": str(req.execution_id),
        "toolCalls": 0,
        "usageTurns": [],
    })
    try:
        max_calls = int(req.context.get("maxToolCalls", 8))
        result = await graph.ainvoke(
            {"messages": [{"role": "user", "content": req.message}], "workflow_context": {"workflowType": req.context.get("workflowType", "FREEFORM"), "options": req.context.get("options", {})}},
            config={"recursion_limit": max(25, max_calls * 2 + 5)},
        )
        last = result["messages"][-1]
        usage = aggregate_usage(request_context.get()["usageTurns"])
        return {"answer": last.content, "usage": usage.to_dict()}
    except Exception as exc:
        logger.warning(
            "Agent execution failed task_id=%s execution_id=%s error_type=%s",
            req.context.get("taskId"),
            req.execution_id,
            type(exc).__name__,
        )
        raise HTTPException(status_code=502, detail="Agent execution failed") from None
    finally:
        request_context.reset(token)
