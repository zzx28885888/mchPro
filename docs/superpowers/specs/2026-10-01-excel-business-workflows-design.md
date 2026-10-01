# Excel Business Workflows — Phase 1 Design

## Status

Approved conversational design; written specification awaiting user review.

## Goal

Add three trustworthy, reusable business workflows to the existing Excel AI SaaS: multi-file merge and cleanup, two-workbook reconciliation, and sales/operations summary reporting.

## Users and success

The initial users are business operators, sales/operations staff, and finance staff who receive recurring Excel exports and need a usable result without writing formulas or scripts. A successful workflow makes its input scope and processing rules visible, produces a separate downloadable workbook, and explains row counts and exceptions so the user can verify the result.

## Current product context

- Spring Boot owns authentication, file ownership, task lifecycle, plan/quota checks, and the Java `@AgentTool` registry.
- The Python LangGraph service plans and orchestrates tasks; Excel data operations remain in Java tools.
- `/api/tasks` currently accepts one uploaded `fileId` and a natural-language `prompt`.
- `ExcelService` currently reads and writes one sheet as row maps. `ExcelAgentTools` currently offers read, exact-value filter, sort, top-N, and export.
- Task execution is asynchronous. Input-file ownership and tool permissions are enforced through trusted backend context.

## Phase 1 user experience

The authenticated user chooses one of three workflow starters, uploads the required workbook or workbooks, and describes any business-specific rules. Before processing, the task summary should identify the selected workflow, input files and sheets, recognized fields or field mapping, and planned operation rules. The task then runs asynchronously and returns a new result workbook plus a concise processing summary. The original input files remain unchanged.

The existing free-form task entry remains available. Workflow starters should provide structured prompts/defaults to reduce ambiguity, while the Agent may ask for missing required fields instead of guessing.

### Workflow A: Merge and clean

- Accept multiple uploaded workbooks and selected sheets.
- Combine compatible tabular data, map equivalent headers, and report unmapped/conflicting columns.
- Support configured duplicate removal, blank-row/empty-value handling, and date/amount normalization.
- Export the merged dataset and a processing summary with source and output row counts and skipped/duplicate rows.

### Workflow B: Reconcile two workbooks

- Accept two uploaded workbooks and selected sheets.
- Let the user identify one or more matching keys and, when applicable, numeric fields to compare with an explicit tolerance.
- Produce matched rows, records only present on either side, and rows with value differences.
- Include match status, compared values, and a summary of each result category.

### Workflow C: Sales/operations summary

- Accept one workbook and a selected sheet.
- Let the user identify a measure and optional time/category dimensions (such as date, region, channel, or product).
- Produce grouped totals/counts, rankings, and basic time-series/category charts when the source supports them.
- Include the aggregation and period definitions in the result summary; AI-generated commentary must refer to computed output and be labeled as interpretation.

## Architecture and data flow

1. The browser uploads each input through the existing owner-scoped file API.
2. Task creation accepts a bounded list of input file IDs, a workflow type, and workflow parameters/instructions. The backend validates ownership, per-file and aggregate limits, plan permissions, and quotas before dispatch.
3. Trusted task context carries the authenticated user, task ID, and validated input file IDs to LangGraph and Java. Model-generated arguments cannot grant access to files or users.
4. LangGraph plans only within the selected workflow and calls Java registry tools for workbook reading, transformation, reconciliation/aggregation, and export.
5. Java validates requested columns and parameters against the actual workbook schema, applies configured limits, records auditable operation summaries, and writes outputs to private storage as separate result files.
6. The existing task status/result flow exposes completion and downloadable result file IDs. The user receives a processing summary with row counts, exceptions, and operation rules.

PostgreSQL remains the durable source of task/file metadata; Redis remains task progress and temporary processing state. Existing authentication, ownership, plan enforcement, and audit boundaries remain authoritative.

## Product safeguards

- Do not overwrite or mutate source workbooks.
- Write all comments and docstrings added or modified for this phase in both Chinese and English; keep the two language versions semantically aligned.
- Show chosen files, sheets, field mappings, matching keys, tolerances, grouping dimensions, and key operations before execution.
- Reject ambiguous or missing required mappings rather than silently selecting a potentially incorrect column.
- Preserve traceable row-level exception results for unmatched, duplicate, invalid, or skipped data.
- State row counts and calculation definitions in the output summary; AI commentary cannot be presented as a computed fact.
- Enforce existing file-size, quota, timeout, and tool-call controls, adding aggregate multi-file limits and operation-specific limits where needed.
- Treat uploaded workbook content as untrusted data, never as instructions to the Agent.

## Out of scope for Phase 1

- Scheduled/recurring execution and saved workflow templates.
- Direct ERP/CRM/database connectors or external data synchronization.
- Editing source workbooks in place or collaborative workbook editing.
- OCR/PDF ingestion, macros/VBA generation, arbitrary code execution, and advanced forecasting.
- A full BI dashboard product or general-purpose spreadsheet editor.

## Rollout sequence

Deliver shared multi-sheet/multi-file reading and validated transformation primitives first; then add merge/clean, reconciliation, and grouped reporting as separate end-to-end workflow slices. Keep existing single-file free-form tasks working. Exact API fields, database migration, tool schemas, result format, plan grants, and test cases are to be specified in the implementation plan after this specification is approved.

## Open implementation decisions

- Choose a safe bounded aggregate upload limit consistent with existing plan sizes and storage constraints.
- Choose the chart-writing approach compatible with the current Excel library/dependency set.
- Decide whether the pre-execution task summary is a persisted preview state or a task-creation response validated synchronously; the choice must preserve clear user review before costly/destructive actions, while source files remain immutable.

## Self-review

- The three workflows share a common trust boundary and output lifecycle, but remain independently deliverable slices.
- Inputs, output behavior, and Phase 1 exclusions are explicit.
- Column ambiguity, reconciliation tolerance, traceability, and AI commentary risks are addressed.
- Implementation details intentionally left open are listed as decisions for the plan rather than hidden assumptions.
