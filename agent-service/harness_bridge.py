"""
Optional DeepSeek Harness bridge.

Harness is deliberately isolated from the core business Agent.
Use this when you want DSH to drive an isolated workspace or developer task.
Do not expose this endpoint to untrusted users.
"""

import os
from deepseek_harness import DeepSeekHarness


def run_harness(task: str, workspace: str):
    """中文：在指定工作区运行一次 Harness 开发任务并返回最终文本。
    English: Run one Harness developer task in the specified workspace and return its final response.
    """
    # 中文：Harness 是开发者工作流，不是产品 Agent。workspace 决定其工作目录，调用方必须限制目录范围。
    # English: Harness is for developer workflows, not product requests. The workspace sets its working directory and must be scoped by the caller.
    home = os.path.abspath(os.getenv("DSH_HOME", ".dsh-home"))
    with DeepSeekHarness(
        dsh_home=home,
        cwd=os.path.abspath(workspace),
        provider=os.getenv("DSH_PROVIDER", "deepseek-official"),
        model=os.getenv("DSH_MODEL", "deepseek-v4-flash"),
        api_key=os.getenv("DEEPSEEK_API_KEY"),
        base_url=os.getenv("DEEPSEEK_BASE_URL") or None,
        request_timeout_seconds=300,
    ) as harness:
        result = harness.run(task)
        return result.final_response
