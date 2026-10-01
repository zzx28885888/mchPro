"""中文：LangGraph 服务的 HTTP 入口。English: HTTP entry point for the LangGraph service."""

import os

from dotenv import load_dotenv
from fastapi import FastAPI
from pydantic import BaseModel

from agent_graph import build_graph
from java_tool_client import JavaToolRegistryClient
from java_tool_client import request_context

load_dotenv()

# 中文：构建一次图以复用模型客户端和节点定义；业务状态仍按每次请求单独传入。
# English: Build the graph once to reuse model clients and node definitions; request state is passed separately per call.
app = FastAPI(title="Low Cost AI Agent V1.6")
graph = build_graph()
java_client = JavaToolRegistryClient()


class ChatRequest(BaseModel):
    """中文：message 是用户意图；context 是后端签发的任务上下文，不是模型工具参数。
    English: message is the user intent; context is trusted task metadata from the backend, not a model tool argument.
    """
    message: str
    context: dict = {}


@app.get("/health")
async def health():
    return {"status": "UP", "version": "v1.6"}


@app.get("/tools")
async def tools():
    # 中文：只返回 Java 注册表定义；此路由不执行工具。
    # English: Returns Java registry definitions only; this route does not execute tools.
    return {"tools": await java_client.list_tools()}


@app.post("/agent/chat")
async def chat(req: ChatRequest):
    # 中文：ContextVar 让同一请求的并发工具协程读取到相同任务身份；finally 防止复用任务残留上下文。
    # English: ContextVar propagates this request's identity to tool coroutines; finally prevents context leakage between reused tasks.
    token = request_context.set({**req.context, "toolCalls": 0})
    try:
        max_calls = int(req.context.get("maxToolCalls", 8))
        result = await graph.ainvoke(
            {"messages": [{"role": "user", "content": req.message}], "workflow_context": {"workflowType": req.context.get("workflowType", "FREEFORM"), "options": req.context.get("options", {})}},
            config={"recursion_limit": max(25, max_calls * 2 + 5)},
        )
    finally:
        request_context.reset(token)
    last = result["messages"][-1]
    return {
        "answer": last.content,
        "messages": [
            {
                "type": m.__class__.__name__,
                "content": getattr(m, "content", ""),
                "tool_calls": getattr(m, "tool_calls", []),
            }
            for m in result["messages"]
        ],
    }
