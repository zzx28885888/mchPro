"""中文：验证 Agent 聊天路由只返回回答和用量摘要。
English: Verifies the Agent chat route returns only the answer and usage summary.
"""

import asyncio
import importlib
import sys
from uuid import uuid4

from fastapi.testclient import TestClient
from langchain_core.messages import AIMessage, HumanMessage

from usage_metering import TurnUsage


def _load_app(monkeypatch):
    monkeypatch.setenv("AGENT_INTERNAL_TOKEN", "test-agent-token")
    monkeypatch.setenv("LLM_PROVIDER", "ollama")
    sys.modules.pop("app", None)
    return importlib.import_module("app")


def test_chat_returns_answer_and_aggregated_usage_without_raw_messages(monkeypatch):
    agent_app = _load_app(monkeypatch)
    observed = {}

    class FakeGraph:
        async def ainvoke(self, state, config):
            observed["execution_id"] = agent_app.request_context.get()["executionId"]
            observed["task_id"] = agent_app.request_context.get()["taskId"]
            agent_app.request_context.get()["usageTurns"].extend(
                [
                    TurnUsage("ollama", "qwen3.5:9b", 10, 2, 1, 12),
                    TurnUsage("ollama", "qwen3.5:9b", 8, 3, 0, 15),
                ]
            )
            return {"messages": [HumanMessage(content="private prompt"), AIMessage(content="finished") ]}

    agent_app.graph = FakeGraph()
    execution_id = str(uuid4())
    with TestClient(agent_app.app) as client:
        response = client.post(
            "/agent/chat",
            headers={"X-Agent-Token": "test-agent-token"},
            json={"message": "do task", "execution_id": execution_id, "context": {"taskId": 42}},
        )

    assert response.status_code == 200
    assert observed == {"execution_id": execution_id, "task_id": 42}
    assert response.json() == {
        "answer": "finished",
        "usage": {
            "provider_code": "ollama",
            "model_name": "qwen3.5:9b",
            "input_tokens": 18,
            "output_tokens": 5,
            "cached_input_tokens": 1,
            "model_call_count": 2,
            "llm_duration_ms": 27,
            "measurement_status": "COMPLETE",
        },
    }


def test_chat_rejects_missing_or_invalid_execution_uuid(monkeypatch):
    agent_app = _load_app(monkeypatch)

    with TestClient(agent_app.app) as client:
        missing = client.post(
            "/agent/chat",
            headers={"X-Agent-Token": "test-agent-token"},
            json={"message": "do task", "context": {"taskId": 42}},
        )
        invalid = client.post(
            "/agent/chat",
            headers={"X-Agent-Token": "test-agent-token"},
            json={"message": "do task", "execution_id": "not-a-uuid", "context": {"taskId": 42}},
        )

    assert missing.status_code == 422
    assert invalid.status_code == 422


def test_chat_hides_agent_exception_details(monkeypatch):
    agent_app = _load_app(monkeypatch)

    class FailingGraph:
        async def ainvoke(self, state, config):
            raise RuntimeError("private prompt and model response")

    agent_app.graph = FailingGraph()
    with TestClient(agent_app.app, raise_server_exceptions=False) as client:
        response = client.post(
            "/agent/chat",
            headers={"X-Agent-Token": "test-agent-token"},
            json={"message": "private prompt", "execution_id": str(uuid4()), "context": {"taskId": 42}},
        )

    assert response.status_code == 502
    assert "private prompt" not in response.text
    assert "model response" not in response.text
