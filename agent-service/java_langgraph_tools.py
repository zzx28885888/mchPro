"""中文：定义供 LLM 选择的参数 Schema，并把每个调用代理到 Java 工具注册表。
English: Defines LLM-facing argument schemas and proxies each call to the Java Tool Registry.
"""

from typing import Any
from langchain_core.tools import StructuredTool
from pydantic import BaseModel, ConfigDict, Field
from java_tool_client import JavaToolRegistryClient

# 中文：客户端复用服务地址与内部令牌配置；真正的权限检查仍由 Java 后端完成。
# English: The client reuses registry URL/token configuration; Java remains responsible for authorization.
client = JavaToolRegistryClient()


class ReadArgs(BaseModel):
    """中文：读取当前任务绑定的输入文件，不接受模型指定路径或用户 ID。
    English: Reads the input bound to the current task; no model-supplied path or user ID is accepted.
    """
    model_config = ConfigDict(extra="forbid")


class FilterArgs(BaseModel):
    """中文：精确筛选条件；列和值均来自模型对用户请求的结构化理解。
    English: Exact-match filter criteria structured by the model from the user's request.
    """
    column: str = Field(description="Column name")
    value: str = Field(description="Exact text value to match")


class SortArgs(BaseModel):
    """中文：排序列和方向。English: Column and direction used for sorting."""
    column: str = Field(description="Column name")
    ascending: bool = Field(default=True, description="Sort ascending")


class TopArgs(BaseModel):
    """中文：保留前 N 行的数量。English: Number of leading rows to retain."""
    count: int = Field(description="Number of rows to retain", ge=1)


class EmptyArgs(BaseModel):
    """中文：这些工具只使用已确认任务选项，不接受模型传入的文件 ID 或参数。
    English: These tools use confirmed task options and accept no model-supplied file IDs or operation settings.
    """
    model_config = ConfigDict(extra="forbid")


async def call(name: str, args: dict[str, Any]) -> Any:
    # 中文：统一处理成功/失败响应，避免每个工具重复编写 HTTP 错误判断。
    # English: Centralize response/error handling so individual tool wrappers stay small.
    result = await client.execute(name, args)
    if not result.get("success"):
        raise RuntimeError(result.get("error") or "Java tool failed")
    return result.get("data")


async def read_excel() -> Any: return await call("read_excel", {})
async def filter_rows(column: str, value: str) -> Any: return await call("filter", {"column": column, "value": value})
async def sort_rows(column: str, ascending: bool = True) -> Any: return await call("sort", {"column": column, "ascending": ascending})
async def top_rows(count: int) -> Any: return await call("top", {"count": count})
async def export_excel() -> Any: return await call("export_excel", {})
async def inspect_workflow_inputs() -> Any: return await call("inspect_workflow_inputs", {})
async def merge_clean_workbooks() -> Any: return await call("merge_clean_workbooks", {})
async def reconcile_workbooks() -> Any: return await call("reconcile_workbooks", {})
async def summarize_workbook() -> Any: return await call("summarize_workbook", {})
async def export_workbook_result() -> Any: return await call("export_workbook_result", {})


# 中文：名称必须与 Java @AgentTool 和 plan_tool_permission 中的 tool_name 完全一致。
# English: Names must match Java @AgentTool definitions and plan_tool_permission.tool_name values exactly.
JAVA_TOOLS = [
    StructuredTool.from_function(coroutine=read_excel, name="read_excel", description="Read the uploaded Excel workbook for this task. Always call this first.", args_schema=ReadArgs),
    StructuredTool.from_function(coroutine=filter_rows, name="filter", description="Filter Excel rows by exact text in a column.", args_schema=FilterArgs),
    StructuredTool.from_function(coroutine=sort_rows, name="sort", description="Sort Excel rows by a column.", args_schema=SortArgs),
    StructuredTool.from_function(coroutine=top_rows, name="top", description="Keep only the first N rows.", args_schema=TopArgs),
    StructuredTool.from_function(coroutine=export_excel, name="export_excel", description="Write the current rows to a downloadable Excel result. Call last.", args_schema=EmptyArgs),
    StructuredTool.from_function(coroutine=inspect_workflow_inputs, name="inspect_workflow_inputs", description="Inspect trusted workbook sheets and headers before a selected business workflow.", args_schema=EmptyArgs),
    StructuredTool.from_function(coroutine=merge_clean_workbooks, name="merge_clean_workbooks", description="Execute confirmed multi-workbook merge and cleanup rules.", args_schema=EmptyArgs),
    StructuredTool.from_function(coroutine=reconcile_workbooks, name="reconcile_workbooks", description="Execute confirmed two-workbook reconciliation keys and tolerance.", args_schema=EmptyArgs),
    StructuredTool.from_function(coroutine=summarize_workbook, name="summarize_workbook", description="Execute the confirmed grouped workbook summary.", args_schema=EmptyArgs),
    StructuredTool.from_function(coroutine=export_workbook_result, name="export_workbook_result", description="Export a successful deterministic workflow result and its audit summary.", args_schema=EmptyArgs),
]
