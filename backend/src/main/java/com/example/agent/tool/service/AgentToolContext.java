package com.example.agent.tool.service;

import java.util.Map;

/**
 * 中文：在工具执行线程中暂存当前任务的可信上下文；每次调用结束必须清理 ThreadLocal。
 * English: Holds trusted task context on the tool-execution thread; always clear the ThreadLocal after each call.
 */
public final class AgentToolContext {
    private static final ThreadLocal<Map<String, Object>> CURRENT = new ThreadLocal<>();

    private AgentToolContext() {
    }

    public static void set(Map<String, Object> context) {
        CURRENT.set(context);
    }

    public static Map<String, Object> get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
