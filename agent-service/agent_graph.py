"""中文：定义 LangGraph 对话状态、LLM 选择和工具调用循环。
English: Defines LangGraph conversation state, provider selection, and the tool-calling loop.
"""

import os
from typing import Annotated, TypedDict

from langchain_core.messages import BaseMessage, HumanMessage, SystemMessage
from langchain_ollama import ChatOllama
from langchain_openai import ChatOpenAI
from langgraph.graph import END, START, StateGraph
from langgraph.graph.message import add_messages
from langgraph.prebuilt import ToolNode

from java_langgraph_tools import JAVA_TOOLS


class AgentState(TypedDict):
    """中文：LangGraph 在节点之间传递的状态；add_messages 负责追加而非覆盖对话历史。
    English: State passed between graph nodes; add_messages appends new messages instead of replacing conversation history.
    """
    messages: Annotated[list[BaseMessage], add_messages]


# 中文：系统提示约束模型只做业务意图理解；真正的文件操作必须交给 Java Tool Registry。
# English: The system prompt limits the model to business intent; actual file operations must go through the Java Tool Registry.
SYSTEM_PROMPT = """You are a controlled business agent.
You process the uploaded Excel workbook for the current task. For every request that transforms, analyzes, or exports spreadsheet data, call read_excel first, then only the needed filter/sort/top tools, and call export_excel last. Never claim a file was created unless export_excel succeeds.
You may access business data ONLY through the supplied Java Tool Registry tools.
Never invent database access, filesystem access, SQL, shell commands, or hidden APIs.
If a tool fails or times out, explain that the controlled tool failed.
Do not bypass permissions.
"""


def build_graph():
    # 中文：由配置选择本地 Ollama 或远程 OpenAI 兼容接口；远程模式缺少必填项时启动失败，尽早暴露配置错误。
    # English: Select local Ollama or a remote OpenAI-compatible provider; fail fast when required remote settings are missing.
    provider = os.getenv("LLM_PROVIDER", "ollama").strip().lower()
    if provider in {"remote", "openai", "openai-compatible"}:
        api_key = os.getenv("REMOTE_LLM_API_KEY", "").strip()
        model_name = os.getenv("REMOTE_LLM_MODEL", "").strip()
        if not api_key:
            raise RuntimeError(
                "LLM_PROVIDER is remote but REMOTE_LLM_API_KEY is not configured"
            )
        if not model_name:
            raise RuntimeError(
                "LLM_PROVIDER is remote but REMOTE_LLM_MODEL is not configured"
            )
        model = ChatOpenAI(
            model=model_name,
            api_key=api_key,
            base_url=os.getenv("REMOTE_LLM_BASE_URL", "").strip() or None,
            temperature=0,
        )
    else:
        model = ChatOllama(
            model=os.getenv("OLLAMA_MODEL", "qwen3:8b"),
            base_url=os.getenv("OLLAMA_BASE_URL", "http://localhost:11434"),
            temperature=0,
        )
    # 中文：把 Python 中的工具 Schema 绑定给 LLM；这些工具实现只是 Java 注册表的 HTTP 代理。
    # English: Bind model-facing schemas; their Python implementations only proxy calls to the Java registry.
    model = model.bind_tools(JAVA_TOOLS)

    async def agent(state: AgentState):
        messages = state["messages"]
        if not messages or not isinstance(messages[0], SystemMessage):
            messages = [SystemMessage(content=SYSTEM_PROMPT), *messages]
        response = await model.ainvoke(messages)
        return {"messages": [response]}

    def route(state: AgentState):
        last = state["messages"][-1]
        if getattr(last, "tool_calls", None):
            return "tools"
        return END

    # 中文：agent 节点决定下一步；有 tool_calls 时转到 tools，工具结果再回到 agent，直到模型给出最终回答。
    # English: The agent chooses the next step; tool calls route to tools, and tool results loop back until a final answer is produced.
    graph = StateGraph(AgentState)
    graph.add_node("agent", agent)
    graph.add_node("tools", ToolNode(JAVA_TOOLS))
    graph.add_edge(START, "agent")
    graph.add_conditional_edges("agent", route, {"tools": "tools", END: END})
    graph.add_edge("tools", "agent")
    return graph.compile()
