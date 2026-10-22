package com.excelai.agent;

/** 中文/English：Agent 回答和不含对话内容的用量摘要 / Agent answer and metering-only usage summary. */
public record AgentResult(String answer, AgentUsageSummary usage) {
}
