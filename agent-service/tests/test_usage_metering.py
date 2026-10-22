"""中文：验证模型用量字段的提取和多轮汇总。
English: Verifies model usage extraction and multi-turn aggregation.
"""

import sys
import asyncio
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from types import SimpleNamespace

from langchain_core.messages import AIMessage
from langchain_core.runnables import RunnableLambda

import agent_graph
from java_tool_client import request_context
from usage_metering import aggregate_usage, extract_turn_usage


def test_aggregates_complete_usage_across_model_turns():
    turns = [
        extract_turn_usage(
            SimpleNamespace(usage_metadata={"input_tokens": 10, "output_tokens": 4, "input_token_details": {"cache_read": 2}}),
            "ollama",
            "qwen3.5:9b",
            duration_ms=12,
        ),
        extract_turn_usage(
            SimpleNamespace(usage_metadata={"input_tokens": 8, "output_tokens": 3, "input_token_details": {"cache_read": 0}}),
            "ollama",
            "qwen3.5:9b",
            duration_ms=18,
        ),
    ]

    summary = aggregate_usage(turns)

    assert summary.input_tokens == 18
    assert summary.output_tokens == 7
    assert summary.cached_input_tokens == 2
    assert summary.model_call_count == 2
    assert summary.llm_duration_ms == 30
    assert summary.measurement_status == "COMPLETE"


def test_marks_partial_when_one_turn_lacks_usage_without_replacing_missing_with_zero():
    turns = [
        extract_turn_usage(SimpleNamespace(usage_metadata={"input_tokens": 10, "output_tokens": 4}), "remote", "deepseek-flash", duration_ms=5),
        extract_turn_usage(SimpleNamespace(usage_metadata=None), "remote", "deepseek-flash", duration_ms=7),
    ]

    summary = aggregate_usage(turns)

    assert summary.input_tokens is None
    assert summary.output_tokens is None
    assert summary.cached_input_tokens is None
    assert summary.model_call_count == 2
    assert summary.measurement_status == "PARTIAL"


def test_marks_unavailable_when_all_turns_lack_token_counts():
    turns = [
        extract_turn_usage(SimpleNamespace(usage_metadata=None), "remote", "deepseek-flash", duration_ms=3),
        extract_turn_usage(SimpleNamespace(usage_metadata={}), "remote", "deepseek-flash", duration_ms=4),
    ]

    summary = aggregate_usage(turns)

    assert summary.input_tokens is None
    assert summary.output_tokens is None
    assert summary.cached_input_tokens is None
    assert summary.measurement_status == "UNAVAILABLE"


def test_preserves_measured_zero_and_maps_ollama_response_metadata():
    response = SimpleNamespace(
        usage_metadata=None,
        response_metadata={
            "prompt_eval_count": 0,
            "prompt_eval_cached_count": 0,
            "eval_count": 0,
        },
    )

    turn = extract_turn_usage(response, "ollama", "qwen3.5:9b", duration_ms=9)
    summary = aggregate_usage([turn])

    assert summary.input_tokens == 0
    assert summary.output_tokens == 0
    assert summary.cached_input_tokens == 0
    assert summary.measurement_status == "COMPLETE"


def test_keeps_cached_count_missing_when_provider_does_not_report_it():
    response = SimpleNamespace(usage_metadata={"input_tokens": 10, "output_tokens": 4})

    turn = extract_turn_usage(response, "remote", "deepseek-flash", duration_ms=9)
    summary = aggregate_usage([turn])

    assert summary.input_tokens == 10
    assert summary.output_tokens == 4
    assert summary.cached_input_tokens is None
    assert summary.measurement_status == "PARTIAL"


def test_agent_graph_records_usage_for_each_successful_model_response(monkeypatch):
    response = AIMessage(
        content="done",
        usage_metadata={"input_tokens": 6, "output_tokens": 2, "total_tokens": 8, "input_token_details": {"cache_read": 0}},
    )

    class FakeChatModel:
        def bind_tools(self, tools):
            return RunnableLambda(lambda messages: response)

    monkeypatch.setattr(agent_graph, "ChatOllama", lambda **kwargs: FakeChatModel())
    token = request_context.set({"usageTurns": []})
    try:
        asyncio.run(
            agent_graph.build_graph().ainvoke(
                {"messages": [{"role": "user", "content": "hello"}], "workflow_context": {}}
            )
        )
        turns = request_context.get()["usageTurns"]
    finally:
        request_context.reset(token)

    assert len(turns) == 1
    assert turns[0].input_tokens == 6
    assert turns[0].output_tokens == 2
    assert turns[0].cached_input_tokens == 0
