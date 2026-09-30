package com.example.agent.tool.registry;

import com.example.agent.tool.model.ToolDefinition;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 中文：注册表运行时保存的工具实例，包括描述、Spring Bean、反射方法和参数定义。
 * English: Runtime registry entry containing the definition, Spring bean, reflected method, and parameter metadata.
 */
public record RegisteredTool(
        ToolDefinition definition,
        Object bean,
        Method method,
        Map<String, ParameterSpec> parameters
) {
    public record ParameterSpec(
            String name,
            String description,
            boolean required,
            Class<?> type
    ) {
    }
}
