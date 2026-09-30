"""中文：项目最早期的 Python 假数据工具，仅保留作学习示例；Excel SaaS 现已使用 Java 工具。
English: Early Python mock tools retained for learning; the Excel SaaS now uses Java tools.
"""

from .registry import ToolRegistry, ToolSpec


def get_user(user_id: int):
    # V1 demo only. In production this must call a controlled service/repository.
    users = {
        10001: {"id": 10001, "name": "Alice", "level": "PRO"},
        10002: {"id": 10002, "name": "Bob", "level": "FREE"},
    }
    return users.get(user_id, {"id": user_id, "name": "Unknown"})


def get_order(order_id: int):
    orders = {
        20001: {"id": 20001, "user_id": 10001, "status": "PAID", "amount": 99.0},
        20002: {"id": 20002, "user_id": 10002, "status": "PENDING", "amount": 29.0},
    }
    return orders.get(order_id, {"id": order_id, "status": "NOT_FOUND"})


def query_excel(question: str):
    # 中文：占位实现，不会读取真实文件；实际处理路径是 java_langgraph_tools.py -> Java @AgentTool。
    # English: Placeholder only; real workbook processing flows through java_langgraph_tools.py to Java @AgentTool methods.
    # V1 placeholder. V2 will connect to EasyExcel/Polars/Pandas via a sandboxed worker.
    return {
        "question": question,
        "rows_analyzed": 0,
        "message": "ExcelTool placeholder: connect your EasyExcel/Python worker here."
    }


def build_registry() -> ToolRegistry:
    registry = ToolRegistry()
    registry.register(ToolSpec(
        name="getUser",
        description="Query a user by numeric user_id.",
        permission="user:read",
        timeout_seconds=3,
        handler=get_user,
    ))
    registry.register(ToolSpec(
        name="getOrder",
        description="Query an order by numeric order_id.",
        permission="order:read",
        timeout_seconds=3,
        handler=get_order,
    ))
    registry.register(ToolSpec(
        name="queryExcel",
        description="Analyze an uploaded Excel dataset using a natural-language question.",
        permission="excel:read",
        timeout_seconds=30,
        handler=query_excel,
    ))
    return registry
