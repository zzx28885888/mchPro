"""中文：验证 Python 暴露的工作流 Schema 与 Java 注册表工具名称保持一致。
English: Verifies Python workflow schemas stay aligned with Java registry tool names.
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import pytest
from pydantic import ValidationError
from java_langgraph_tools import JAVA_TOOLS


def test_python_schemas_include_every_java_workflow_tool():
    expected = {
        "inspect_workflow_inputs",
        "merge_clean_workbooks",
        "reconcile_workbooks",
        "summarize_workbook",
        "export_workbook_result",
    }
    tools = {tool.name: tool for tool in JAVA_TOOLS}
    assert expected <= tools.keys()
    for name in expected:
        schema = tools[name].args_schema.model_json_schema()
        assert schema.get("properties", {}) == {}
        assert schema.get("additionalProperties") is False


def test_workflow_tool_schemas_reject_model_supplied_file_and_user_ids():
    tools = {tool.name: tool for tool in JAVA_TOOLS}
    for name in ("inspect_workflow_inputs", "merge_clean_workbooks", "reconcile_workbooks", "summarize_workbook", "export_workbook_result"):
        with pytest.raises(ValidationError):
            tools[name].args_schema.model_validate({"fileId": 99, "userId": 7, "path": "C:/private.xlsx"})
