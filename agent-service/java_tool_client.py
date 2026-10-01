"""中文：LangGraph 到 Spring Java Tool Registry 的 HTTP 客户端。
English: HTTP client connecting LangGraph to the Spring Java Tool Registry.
"""

import os
from typing import Any, Dict

import httpx
from contextvars import ContextVar

# 中文：每个聊天请求单独设置上下文，避免多个并发用户之间串用 userId/taskId。
# English: Each chat request gets its own context to prevent userId/taskId leakage between concurrent users.
request_context: ContextVar[dict] = ContextVar("agent_request_context", default={})


class JavaToolRegistryClient:
    """中文：Java 是唯一真实工具实现与授权边界；Python 这里只负责 HTTP 转发。
    English: Java owns the actual tool implementations and authorization boundary; Python only forwards HTTP calls.
    """

    def __init__(self):
        self.base_url = os.getenv(
            "JAVA_TOOL_REGISTRY_URL",
            "http://localhost:8080/internal/agent/tools"
        ).rstrip("/")
        self.principal = os.getenv("JAVA_TOOL_PRINCIPAL", "langgraph")
        self.timeout = float(os.getenv("JAVA_TOOL_HTTP_TIMEOUT", "10"))
        self.internal_token = os.getenv("AGENT_INTERNAL_TOKEN", "")
        self.headers = {"X-Agent-Token": self.internal_token}

    async def list_tools(self) -> list[dict]:
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            response = await client.get(self.base_url, headers=self.headers)
            response.raise_for_status()
            return response.json()

    async def execute(self, name: str, arguments: Dict[str, Any]) -> Dict[str, Any]:
        # 中文：在 Agent 侧先执行套餐工具次数上限；Java 仍独立校验任务、文件所有权与工具许可。
        # English: Enforce the plan's call-count limit here, while Java independently validates task, ownership, and tool grant.
        ctx = request_context.get()
        if ctx:
            limit = int(ctx.get("maxToolCalls", 8))
            calls = int(ctx.get("toolCalls", 0))
            if calls >= limit:
                raise RuntimeError("Plan tool call limit reached")
            ctx["toolCalls"] = calls + 1
        payload = {
            "arguments": arguments,
            "principal": self.principal,
            "context": {k: ctx[k] for k in ("userId", "taskId", "inputFileId", "inputFileIds", "workflowType", "options", "maxToolCalls") if k in ctx},
        }
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            response = await client.post(
                f"{self.base_url}/{name}/execute",
                json=payload,
                headers=self.headers,
            )
            response.raise_for_status()
            return response.json()
