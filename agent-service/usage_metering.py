"""中文：提取并汇总模型响应中的计量元数据。
English: Extracts and aggregates metering metadata from model responses.
"""

from dataclasses import asdict, dataclass
from typing import Literal, Sequence


MeasurementStatus = Literal["COMPLETE", "PARTIAL", "UNAVAILABLE"]


@dataclass(frozen=True)
class TurnUsage:
    provider_code: str
    model_name: str
    input_tokens: int | None
    output_tokens: int | None
    cached_input_tokens: int | None
    duration_ms: int


@dataclass(frozen=True)
class UsageSummary:
    provider_code: str
    model_name: str
    input_tokens: int | None
    output_tokens: int | None
    cached_input_tokens: int | None
    model_call_count: int
    llm_duration_ms: int
    measurement_status: MeasurementStatus

    def to_dict(self) -> dict[str, str | int | None]:
        return asdict(self)


def _token_count(value: object) -> int | None:
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        return None
    return value


def _first_count(*values: object) -> int | None:
    for value in values:
        count = _token_count(value)
        if count is not None:
            return count
    return None


def extract_turn_usage(
    response: object,
    provider_code: str,
    model_name: str,
    duration_ms: int = 0,
) -> TurnUsage:
    usage = getattr(response, "usage_metadata", None) or {}
    response_metadata = getattr(response, "response_metadata", None) or {}
    provider_usage = response_metadata.get("token_usage") or response_metadata.get("usage") or {}
    input_details = usage.get("input_token_details") or {}
    provider_input_details = provider_usage.get("input_token_details") or {}

    input_tokens = _first_count(
        usage.get("input_tokens"),
        provider_usage.get("prompt_tokens"),
        response_metadata.get("prompt_eval_count"),
    )
    output_tokens = _first_count(
        usage.get("output_tokens"),
        provider_usage.get("completion_tokens"),
        response_metadata.get("eval_count"),
    )
    cached_input_tokens = _first_count(
        input_details.get("cache_read"),
        input_details.get("cached_tokens"),
        provider_input_details.get("cache_read"),
        provider_input_details.get("cached_tokens"),
        provider_usage.get("prompt_cache_hit_tokens"),
        response_metadata.get("prompt_eval_cached_count"),
    )

    return TurnUsage(
        provider_code=provider_code,
        model_name=model_name,
        input_tokens=input_tokens,
        output_tokens=output_tokens,
        cached_input_tokens=cached_input_tokens,
        duration_ms=max(0, duration_ms),
    )


def aggregate_usage(turns: Sequence[TurnUsage]) -> UsageSummary:
    if not turns:
        return UsageSummary("unknown", "unknown", None, None, None, 0, 0, "UNAVAILABLE")

    def total(field: str) -> int | None:
        values = [getattr(turn, field) for turn in turns]
        return sum(values) if all(value is not None for value in values) else None

    input_tokens = total("input_tokens")
    output_tokens = total("output_tokens")
    cached_input_tokens = total("cached_input_tokens")
    reported_counts = [
        value
        for turn in turns
        for value in (turn.input_tokens, turn.output_tokens, turn.cached_input_tokens)
        if value is not None
    ]
    if not reported_counts:
        status: MeasurementStatus = "UNAVAILABLE"
    elif input_tokens is not None and output_tokens is not None and cached_input_tokens is not None:
        status = "COMPLETE"
    else:
        status = "PARTIAL"

    return UsageSummary(
        provider_code=turns[0].provider_code,
        model_name=turns[0].model_name,
        input_tokens=input_tokens,
        output_tokens=output_tokens,
        cached_input_tokens=cached_input_tokens,
        model_call_count=len(turns),
        llm_duration_ms=sum(turn.duration_ms for turn in turns),
        measurement_status=status,
    )
