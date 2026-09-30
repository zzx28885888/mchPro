# V1.5 Architecture

## Responsibility boundaries

### OpenCode
AI coding assistant used by the developer.

### DeepSeek Harness
Privileged developer/automation harness. Optional and isolated.

### LangGraph
Product Agent runtime and stateful workflow orchestration.

### Spring Boot
Business API, authentication, billing, tasks and persistence.

### Tool Registry
Only approved tools can perform business side effects.

## Tool execution

```text
LLM
 |
 | tool call
 v
LangGraph ToolNode
 |
 v
Tool Registry
 |
 +-- whitelist
 +-- permission
 +-- parameter validation (expand in V1.6)
 +-- timeout (enforce with worker boundary in V1.6)
 |
 v
Business Tool
 |
 v
Controlled service/repository
```

The Agent never gets direct PostgreSQL credentials, filesystem paths, shell access,
or arbitrary SQL.

## Multi-agent evolution

V1.5:

```text
User -> Agent -> Tool Registry -> Tool
```

V2:

```text
User
  |
Supervisor
  +-- Excel Agent
  +-- Research Agent
  +-- Report Agent
       |
       +-- Tool Registry
```

LangGraph supports stateful graphs, persistence/checkpointing and multi-agent
patterns; keep the workflow in LangGraph rather than coupling business logic to
DeepSeek Harness.
