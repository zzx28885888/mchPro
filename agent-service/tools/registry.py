"""中文：早期 Python 本地工具注册表示例；当前产品调用以 Spring Java ToolRegistry 为准。
English: Legacy example of a local Python registry; the product path now uses Spring's Java ToolRegistry.
"""

from dataclasses import dataclass
from typing import Any, Callable


@dataclass(frozen=True)
class ToolSpec:
    """中文：演示注册表中的不可变工具定义。English: Immutable tool definition for the demo registry."""
    name: str
    description: str
    permission: str
    timeout_seconds: int
    handler: Callable[..., Any]


class ToolRegistry:
    """中文：简单示例容器，演示注册、查找和权限判断，不参与当前 SaaS 请求链。
    English: Small teaching example for registration, lookup, and permission checks; unused by the SaaS request path.
    """
    def __init__(self):
        self._tools: dict[str, ToolSpec] = {}

    def register(self, spec: ToolSpec):
        if spec.name in self._tools:
            raise ValueError(f"Tool already registered: {spec.name}")
        self._tools[spec.name] = spec

    def list_tools(self):
        return list(self._tools.values())

    def get(self, name: str) -> ToolSpec:
        if name not in self._tools:
            raise KeyError(f"Unknown tool: {name}")
        return self._tools[name]

    def execute(self, name: str, args: dict[str, Any], permissions: set[str]):
        # 中文：先校验工具要求的 permission，再调用 handler；生产场景还需补充参数 Schema 与超时控制。
        # English: Check the required permission before invoking the handler; production use would also need schema validation and timeouts.
        spec = self.get(name)
        if spec.permission not in permissions:
            raise PermissionError(
                f"Permission denied: required={spec.permission}"
            )
        return spec.handler(**args)
