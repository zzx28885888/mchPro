# DeepSeek Harness integration

DeepSeek Harness (`dsh`) is an optional Agent Harness layer.

Official repository:
https://github.com/deepseek-ai/deepseek-harness

Current status: Developer Preview. APIs can change.

## Start Web UI

From this project:

```powershell
npx @deepseek-ai/dsh web --no-open
```

Default:
http://127.0.0.1:3080

Then choose a disposable workspace in the UI.

## Why it is separate

This project does NOT make Harness a hard dependency of the business Agent.

```text
OpenCode
   |
   +--> development

DeepSeek Harness
   |
   +--> isolated agent workspace / developer automation

LangGraph
   |
   +--> product Multi-Agent workflow

Spring Boot
   |
   +--> business API

Tool Registry
   |
   +--> controlled business side effects
```

## Python SDK

The optional Python bridge is in:

```text
agent-service/harness_bridge.py
```

Install:

```powershell
uv pip install deepseek-harness-sdk
```

The SDK should use an isolated `DSH_HOME` and workspace.

Do not expose a generic "run arbitrary Harness task" HTTP endpoint to end users.
Harness can execute model-generated commands/code and access files/network depending
on its configuration. Treat it as privileged developer infrastructure.
